package com.resumeiq.resume;

import com.resumeiq.common.exception.BadRequestException;
import com.resumeiq.common.exception.ConflictException;
import com.resumeiq.common.exception.ErrorCode;
import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.resume.extract.ExtractedText;
import com.resumeiq.resume.extract.FileType;
import com.resumeiq.resume.extract.TextExtractionService;
import com.resumeiq.resume.extract.UnreadableResumeException;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Everything that happens to a resume between the upload button and the database.
 *
 * <p>The order of the checks is the design. Cheap and decisive first: an empty part, then
 * the size, then the format — each one refuses the request before the next spends anything
 * on it, and none of them trust a single byte of what the client said about the file. Only
 * once the content is known to be a PDF or a DOCX of a sane size does it reach a parser,
 * and only then does anything touch the disk.
 *
 * <p>Extraction is synchronous. For a five-megabyte document that is tens of milliseconds,
 * and the alternative — a queue, a worker, a polling endpoint — buys nothing here except
 * three more things to explain and get wrong. The status column already distinguishes
 * "stored" from "read", so moving extraction onto a worker later needs no schema change
 * and no API change.
 *
 * <p>Ownership is not checked in this class so much as it is made impossible to skip: every
 * lookup goes through {@code findByPublicIdAndUserId}, so there is no code path here that
 * could fetch a resume and forget to ask whose it is. A miss returns 404 rather than 403,
 * because "that exists, but not for you" is itself information about another account.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    /** Longest label we will store, matching the column. */
    private static final int MAX_LABEL_LENGTH = 140;

    /** Longest filename we will store, matching the column. Truncated, not refused. */
    private static final int MAX_FILENAME_LENGTH = 255;

    /** Longest failure note we will store, matching the column. */
    private static final int MAX_ERROR_LENGTH = 300;

    private final ResumeRepository resumes;
    private final UserRepository users;
    private final ResumeStorage storage;
    private final TextExtractionService extraction;
    private final ResumeIqProperties.Upload limits;

    public ResumeService(ResumeRepository resumes,
                         UserRepository users,
                         ResumeStorage storage,
                         TextExtractionService extraction,
                         ResumeIqProperties properties) {
        this.resumes = resumes;
        this.users = users;
        this.storage = storage;
        this.extraction = extraction;
        this.limits = properties.upload();
    }

    /**
     * Validates, stores and reads one uploaded file.
     *
     * <p>A file that cannot be read still produces a resume row, marked
     * {@link ResumeStatus#EXTRACTION_FAILED} with a message written for the person who
     * uploaded it. That is deliberate. The bytes are already on disk by then, so the
     * alternatives are to leave an orphaned file behind or to add a second failure path
     * that deletes it — and more importantly, the row is what makes the failure visible.
     * The resume appears in the list saying why it cannot be analysed, and the delete
     * endpoint removes it. A rejection would leave the person with a toast message and no
     * record of what went wrong.
     *
     * @param caller the authenticated owner
     * @param file   the multipart part named {@code file}
     * @param label  optional display name; the filename is used when it is absent
     */
    @Transactional
    public ResumeResponse upload(AuthenticatedUser caller, MultipartFile file, String label) {
        byte[] content = contentOf(file);
        FileType type = identify(content);
        enforceQuota(caller);

        String storageKey = allocateStorageKey(type);
        storage.store(content, storageKey);
        User owner = users.getReferenceById(caller.id());

        Resume.ResumeBuilder builder = Resume.builder()
                .user(owner)
                .label(labelFor(label, file.getOriginalFilename()))
                .originalFilename(displayFilename(file.getOriginalFilename()))
                .contentType(type.contentType())
                .fileSizeBytes(content.length)
                .storageKey(storageKey);

        try {
            ExtractedText extracted = extraction.extract(type, content);
            builder.extractedText(extracted.text())
                    .pageCount(extracted.pageCount())
                    .wordCount(extracted.wordCount())
                    .status(ResumeStatus.TEXT_EXTRACTED);
        } catch (UnreadableResumeException ex) {
            // The message is already written for the user; it is stored so the resume list can
            // explain itself later without re-reading the file. The cause is logged by class
            // name only — a parser's own message can quote the bytes it failed on, and here
            // those bytes are somebody's resume.
            log.info("Text extraction failed for a {} upload: {}",
                    type.label(), causeName(ex));
            builder.status(ResumeStatus.EXTRACTION_FAILED)
                    .extractionError(truncate(ex.getMessage(), MAX_ERROR_LENGTH));
        }

        Resume saved = resumes.save(builder.build());
        log.info("Stored resume {} for user {} ({} bytes, {}, status {})",
                saved.getPublicId(), caller.publicId(), content.length, type.label(), saved.getStatus());
        return ResumeResponse.from(saved);
    }

    /**
     * Every resume this user owns, newest first.
     *
     * <p>Uses the summary projection, so the {@code LONGTEXT} column is not part of the
     * query at all. Twenty resumes is twenty rows of metadata rather than a megabyte of
     * text nobody asked for.
     */
    @Transactional(readOnly = true)
    public List<ResumeResponse> list(AuthenticatedUser caller) {
        return resumes.findSummariesByUserIdOrderByCreatedAtDesc(caller.id()).stream()
                .map(ResumeResponse::from)
                .toList();
    }

    /** One resume, with a short excerpt of what was read from it. */
    @Transactional(readOnly = true)
    public ResumeResponse get(AuthenticatedUser caller, UUID publicId) {
        Resume resume = require(caller, publicId);
        String preview = Optional.ofNullable(resume.getExtractedText())
                .filter(text -> !text.isBlank())
                .map(text -> ExtractedText.of(text, null).preview(ResumeResponse.PREVIEW_LENGTH))
                .orElse(null);
        return ResumeResponse.from(resume, preview);
    }

    /**
     * Deletes the row and the file behind it.
     *
     * <p>The row goes first and the file second, which is the right way round: if the file
     * removal fails, the user has still lost sight of the resume and the leftover bytes are
     * a housekeeping problem. Reversed, a failed row delete would leave a resume in the
     * list whose file no longer exists — a broken record instead of an orphaned file.
     */
    @Transactional
    public void delete(AuthenticatedUser caller, UUID publicId) {
        Resume resume = require(caller, publicId);
        String storageKey = resume.getStorageKey();
        // The owner is in the delete statement as well as in the lookup above. Belt and
        // braces on purpose: the read could be refactored, and this is the layer where a
        // mistake would mean deleting somebody else's resume.
        int removed = resumes.deleteByPublicIdAndUserId(publicId, caller.id());
        if (removed != 1) {
            throw new ResourceNotFoundException("Resume", publicId);
        }
        boolean fileRemoved = storage.delete(storageKey);
        log.info("Deleted resume {} for user {} (file removed: {})",
                publicId, caller.publicId(), fileRemoved);
    }

    /** The ownership-scoped lookup every read and write in this class goes through. */
    private Resume require(AuthenticatedUser caller, UUID publicId) {
        return resumes.findByPublicIdAndUserId(publicId, caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Resume", publicId));
    }

    /**
     * Reads the part and applies the size limit.
     *
     * <p>The container has a limit of its own and rejects an oversized body before it is
     * fully read, which is the one that protects the server. This one protects the
     * invariant: a service that assumes its caller already validated the input is a service
     * that is correct only by luck.
     */
    private byte[] contentOf(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a file to upload.");
        }
        if (file.getSize() > limits.maxFileSizeBytes()) {
            throw new BadRequestException(ErrorCode.FILE_TOO_LARGE,
                    "That file is larger than the %d MB limit."
                            .formatted(limits.maxFileSizeMegabytes()));
        }
        try {
            byte[] content = file.getBytes();
            if (content.length > limits.maxFileSizeBytes()) {
                // getSize() is what the request declared; content.length is what arrived.
                throw new BadRequestException(ErrorCode.FILE_TOO_LARGE,
                        "That file is larger than the %d MB limit."
                                .formatted(limits.maxFileSizeMegabytes()));
            }
            return content;
        } catch (IOException ex) {
            throw new BadRequestException(ErrorCode.UNREADABLE_FILE,
                    "The upload did not complete. Please try again.");
        }
    }

    /**
     * Decides what the file is from its own leading bytes.
     *
     * <p>The {@code Content-Type} header and the filename extension are both supplied by
     * the client and neither is consulted. A legacy {@code .doc} is recognised only so the
     * refusal can be specific, because "unsupported file type" is a dead end and "save it
     * as .docx" is not.
     */
    private FileType identify(byte[] content) {
        return FileType.sniff(content).orElseThrow(() -> {
            if (FileType.isLegacyWordDocument(content)) {
                return new BadRequestException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                        "That is an older Word document (.doc). Open it and save it as .docx, "
                                + "or export it as a PDF, then upload it again.");
            }
            return new BadRequestException(ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Only %s files can be analysed. Whatever this file is named, its contents are "
                            .formatted(FileType.supportedExtensions())
                            + "not one of those formats.");
        });
    }

    /** Keeps one account from filling the disk. The delete endpoint is how you make room. */
    private void enforceQuota(AuthenticatedUser caller) {
        long held = resumes.countByUserId(caller.id());
        if (held >= limits.maxResumesPerUser()) {
            throw new ConflictException(
                    "You have reached the limit of %d saved resumes. Delete one to upload another."
                            .formatted(limits.maxResumesPerUser()));
        }
    }

    /**
     * Takes a storage key and asserts nothing is already filed under it.
     *
     * <p>This is not a collision retry — random UUIDs do not collide, and pretending to
     * handle it would be theatre. It is an invariant check on the key generator itself. The
     * day somebody decides keys should be readable and derives them from the filename or a
     * timestamp, two uploads in the same second start overwriting each other, and this
     * throws instead. The unique index on the column is the real guarantee; this is the
     * cheap version of it that fails before a byte is written rather than after.
     */
    private String allocateStorageKey(FileType type) {
        String key = storage.newStorageKey(type);
        if (resumes.existsByStorageKey(key)) {
            throw new IllegalStateException(
                    "Storage key generator produced a key already in use — keys must be unique");
        }
        return key;
    }

    /**
     * A label for the resume list. Uses what the person typed if they typed anything,
     * otherwise the filename with its extension removed — "Priya CV final v3" reads better
     * in a list than "priya-cv-final-v3.pdf", and either is better than "Untitled".
     */
    private static String labelFor(String label, String originalFilename) {
        if (label != null && !label.isBlank()) {
            return truncate(label.strip(), MAX_LABEL_LENGTH);
        }
        String filename = displayFilename(originalFilename);
        int dot = filename.lastIndexOf('.');
        String withoutExtension = dot > 0 ? filename.substring(0, dot) : filename;
        return truncate(withoutExtension.strip(), MAX_LABEL_LENGTH);
    }

    /**
     * The filename, made safe to <em>show</em>. It is never made safe to use as a path,
     * because it is never used as one: {@link ResumeStorage} names every file itself.
     *
     * <p>Directory components are dropped because Internet Explorer and some mobile
     * browsers send a full local path, and "C:\Users\priya\Desktop\cv.pdf" in a resume list
     * tells everyone who sees a screenshot the person's name and operating system. Control
     * characters go because a newline in a filename can forge a line in a log file.
     */
    private static String displayFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            return "resume";
        }
        String name = originalFilename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cntrl}]", "").strip();
        return name.isEmpty() ? "resume" : truncate(name, MAX_FILENAME_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /** Class name of the underlying cause, for logs that must not quote resume content. */
    private static String causeName(Throwable ex) {
        Throwable cause = ex.getCause();
        return cause == null ? ex.getClass().getSimpleName() : cause.getClass().getSimpleName();
    }
}

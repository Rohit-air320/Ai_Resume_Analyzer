package com.resumeiq.analysis;

import com.resumeiq.common.exception.BadRequestException;
import com.resumeiq.common.exception.ErrorCode;
import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.jobdescription.JobDescription;
import com.resumeiq.jobdescription.JobDescriptionRepository;
import com.resumeiq.resume.Resume;
import com.resumeiq.resume.ResumeRepository;
import com.resumeiq.resume.ResumeStatus;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Resolves the three rows an analysis is about, and checks who owns them.
 *
 * <p>One short read-only transaction that ends before any provider is contacted, which is why it is a
 * bean of its own rather than a method on {@link AnalysisService}: that class must not be transactional
 * at all, and a self-invoked {@code @Transactional} method is a no-op behind Spring's proxy.
 *
 * <p>Both identifiers come from the request body, so both go through {@code findByPublicIdAndUserId}.
 * There is no path through this class that reads a document without asking whose it is.
 */
@Component
public class AnalysisDocuments {

    private final ResumeRepository resumes;
    private final JobDescriptionRepository postings;
    private final UserRepository users;

    public AnalysisDocuments(ResumeRepository resumes, JobDescriptionRepository postings,
                             UserRepository users) {
        this.resumes = resumes;
        this.postings = postings;
        this.users = users;
    }

    /**
     * Loads the resume, the posting and the caller's user row.
     *
     * <p>The resume's extracted text is read here rather than accepted from the request, and that is
     * the rule the whole design rests on: what gets analysed is what was extracted from the file the
     * user uploaded, and no request shape exists that could substitute something else.
     *
     * @throws ResourceNotFoundException if either document is missing or belongs to another account
     * @throws BadRequestException       if the resume yielded no readable text
     */
    @Transactional(readOnly = true)
    public Loaded load(AuthenticatedUser caller, CreateAnalysisRequest request) {
        Resume resume = resumes.findByPublicIdAndUserId(request.resumeId(), caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Resume", request.resumeId()));
        JobDescription posting =
                postings.findByPublicIdAndUserId(request.jobDescriptionId(), caller.id())
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Job description", request.jobDescriptionId()));

        requireReadable(resume);
        User owner = users.findById(caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("User", caller.publicId()));
        return new Loaded(owner, resume, posting);
    }

    /**
     * Refuses a resume there is nothing to read.
     *
     * <p>422 rather than a score. Analysing an empty string produces a confident, low, wrong number —
     * wrong not because the arithmetic slipped but because it measured the extraction failure instead
     * of the resume, and a user would read "ATS score 12" as a verdict on their CV. The upload endpoint
     * already reported why the text could not be read; re-uploading as a text-based PDF is the fix, and
     * saying so is more useful than a number.
     */
    private static void requireReadable(Resume resume) {
        if (resume.getStatus() != ResumeStatus.TEXT_EXTRACTED
                || resume.getExtractedText() == null
                || resume.getExtractedText().isBlank()) {
            throw new BadRequestException(ErrorCode.UNREADABLE_FILE,
                    "We could not read any text from this resume, so there is nothing to score. "
                            + "Upload it again as a text-based PDF or a DOCX rather than a scan.");
        }
    }

    /**
     * The three rows, loaded and ownership-checked.
     *
     * <p>They are detached by the time the analysis runs, which is safe for a reason worth stating:
     * none of the three associations on {@link Analysis} cascades, so Hibernate writes their foreign
     * keys from the identifiers and never tries to reattach them. It is also useful — they are loaded
     * objects rather than proxies, so nothing later needs a session to read a label off them.
     *
     * @param owner   the caller's user row, needed as the analysis's owner
     * @param resume  the resume, with its extracted text
     * @param posting the target job description
     */
    public record Loaded(User owner, Resume resume, JobDescription posting) {

        /** The engine's input, assembled from the two documents. */
        public AnalysisInput toInput() {
            return new AnalysisInput(resume.getExtractedText(), posting.getRawText(),
                    posting.getTitle());
        }
    }
}

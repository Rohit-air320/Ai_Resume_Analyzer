package com.resumeiq.resume;

import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.resume.extract.FileType;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Where uploaded files live on disk.
 *
 * <p>The single most important thing in this class is what it does <em>not</em> do: no
 * part of a storage path ever comes from the uploader. The name a person gave their file
 * is kept for display and nothing else, and the name on disk is a UUID this class
 * generates. That removes path traversal as a category rather than trying to filter for
 * it — there is no sanitising of {@code ../} here because there is nothing to sanitise.
 * A file called {@code ../../../etc/passwd.pdf} is stored as
 * {@code 2026/08/3f9c….pdf} like everything else.
 *
 * <p>Keys are sharded by year and month. Filesystems cope badly with directories holding
 * hundreds of thousands of entries, and a flat layout would eventually become one.
 *
 * <p>Writes use {@code CREATE_NEW}, so a key collision fails loudly instead of silently
 * overwriting somebody else's resume. With a random UUID that will not happen; the point
 * is that if it ever did, it would be an error and not a data loss.
 */
@Service
public class ResumeStorage {

    private static final Logger log = LoggerFactory.getLogger(ResumeStorage.class);

    /** Year/month shard, e.g. {@code 2026/08}. */
    private static final DateTimeFormatter SHARD = DateTimeFormatter.ofPattern("yyyy/MM");

    private final Path root;

    public ResumeStorage(ResumeIqProperties properties) {
        this.root = Paths.get(properties.upload().storageDir()).toAbsolutePath().normalize();
    }

    /**
     * Creates the storage directory at startup rather than on first upload, so a
     * misconfigured or unwritable path is a startup failure — found by whoever is
     * deploying — instead of a 500 for the first person who tries to use the feature.
     */
    @PostConstruct
    void prepareStorageDirectory() {
        try {
            Files.createDirectories(root);
        } catch (IOException ex) {
            throw new IllegalStateException(
                    "Resume storage directory is not usable: " + root
                            + ". Set RESUME_STORAGE_DIR to a writable path.", ex);
        }
        if (!Files.isWritable(root)) {
            throw new IllegalStateException(
                    "Resume storage directory is not writable: " + root
                            + ". Set RESUME_STORAGE_DIR to a writable path.");
        }
        log.info("Resume storage ready at {}", root);
    }

    /**
     * Invents a name for a file nobody else gets to name.
     *
     * <p>Separate from {@link #store} so the caller can confirm the key is unheard of
     * before any bytes are written — see {@code ResumeService}.
     *
     * @param type the sniffed format, which decides the extension
     * @return an opaque storage key, safe to persist and meaningless to a client
     */
    public String newStorageKey(FileType type) {
        return "%s/%s.%s".formatted(
                LocalDate.now().format(SHARD), UUID.randomUUID(), type.extension());
    }

    /**
     * Writes the file under a key from {@link #newStorageKey}.
     *
     * @param content the bytes, already size-checked and type-checked
     */
    public void store(byte[] content, String storageKey) {
        Path destination = resolve(storageKey);
        try {
            Files.createDirectories(destination.getParent());
            Files.write(destination, content, StandardOpenOption.CREATE_NEW,
                    StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not store resume file", ex);
        }
    }

    /**
     * Removes a stored file. A key with no file behind it is not an error: a delete that
     * has already half-succeeded must still be able to finish, and a storage directory
     * that was wiped by hand should not make rows undeletable.
     *
     * @return true if a file was actually removed
     */
    public boolean delete(String storageKey) {
        try {
            return Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ex) {
            // Not fatal, and deliberately not rethrown: the caller is deleting a database row
            // the user asked to be gone, and refusing that because of a filesystem problem
            // leaves them with a resume they cannot get rid of. Logged so it can be cleaned up.
            log.warn("Could not delete stored resume file for key {}: {}",
                    storageKey, ex.getClass().getSimpleName());
            return false;
        }
    }

    /** True when the file behind a key is still present. Used by the tests and diagnostics. */
    public boolean exists(String storageKey) {
        return Files.isRegularFile(resolve(storageKey));
    }

    /** Absolute root of the storage tree. Exposed for logging and tests, not for callers to join onto. */
    public Path root() {
        return root;
    }

    /**
     * Turns a key into a path, and refuses anything that would land outside the storage
     * root.
     *
     * <p>Keys are generated by {@link #store} and nothing else, so this cannot trigger
     * during normal operation. It is here because "cannot" is doing a lot of work in that
     * sentence: keys are read back from the database, and the day something else writes to
     * that column, this check is the difference between a failed request and an arbitrary
     * file being read or deleted.
     */
    private Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key must not be blank");
        }
        Path candidate = root.resolve(storageKey).normalize();
        if (!candidate.startsWith(root)) {
            throw new IllegalArgumentException("Storage key resolves outside the storage root");
        }
        return candidate;
    }
}

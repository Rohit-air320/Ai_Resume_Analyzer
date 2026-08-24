package com.resumeiq.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Resumes, always reached through their owner.
 *
 * <p>Every finder takes a {@code userId} alongside the {@code publicId}. That is the spec's
 * "verify user ownership" rule expressed as a type signature rather than as a habit: there is
 * no {@code findByPublicId} to call by mistake, so the authorisation check cannot be forgotten
 * in a service written six phases from now. A request for someone else's resume returns an
 * empty {@code Optional}, which the service turns into a 404 — not a 403, because confirming
 * that a resume exists but belongs to someone else is itself a small leak.
 */
public interface ResumeRepository extends JpaRepository<Resume, Long> {

    Optional<Resume> findByPublicIdAndUserId(UUID publicId, Long userId);

    /**
     * Backs {@code GET /api/resumes}. Returns the projection, so the text column stays in the
     * database — see {@link ResumeSummary}.
     */
    List<ResumeSummary> findSummariesByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    /** Guards against a storage-key collision before writing a file. */
    boolean existsByStorageKey(String storageKey);

    /**
     * Ownership-scoped delete. Returns the number of rows removed, so the caller can answer
     * 404 without a separate read, and cannot delete another user's row by passing its id.
     *
     * <p>Analyses hold a non-null foreign key to a resume, so the delete service removes those
     * first. That ordering is deliberate rather than pushed into a database-level
     * {@code ON DELETE CASCADE}: an application-level cascade is visible in the code, testable,
     * and lets the same transaction delete the stored file. The foreign key stays as the guard
     * that turns a forgotten step into a loud constraint violation instead of orphaned rows
     * pointing at a resume that no longer exists.
     *
     * <p>{@code @Transactional} is not decoration. Spring Data's generated implementation is
     * {@code @Transactional(readOnly = true)}, which puts Hibernate in {@code FlushMode.MANUAL};
     * a derived delete loads the entities, calls {@code remove}, and then never flushes, so
     * without a writable transaction this method reports rows deleted and deletes nothing. The
     * repository tests cannot catch that — they run inside their own transaction — which is
     * exactly why the annotation belongs here rather than being left to a future caller.
     */
    @Transactional
    int deleteByPublicIdAndUserId(UUID publicId, Long userId);
}

package com.resumeiq.jobdescription;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Job descriptions, always reached through their owner — see {@code ResumeRepository} for why
 * every finder carries a {@code userId}.
 */
public interface JobDescriptionRepository extends JpaRepository<JobDescription, Long> {

    Optional<JobDescription> findByPublicIdAndUserId(UUID publicId, Long userId);

    /**
     * Reuse lookup for a re-pasted posting. Scoped to the user, which is what makes the unique
     * constraint on {@code (user_id, content_hash)} safe to rely on.
     */
    Optional<JobDescription> findByUserIdAndContentHash(Long userId, String contentHash);

    List<JobDescriptionSummary> findSummariesByUserIdOrderByCreatedAtDesc(Long userId);

    long countByUserId(Long userId);

    /** Writable transaction required — see {@code ResumeRepository#deleteByPublicIdAndUserId}. */
    @Transactional
    int deleteByPublicIdAndUserId(UUID publicId, Long userId);
}

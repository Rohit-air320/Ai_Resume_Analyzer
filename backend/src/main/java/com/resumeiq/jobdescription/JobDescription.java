package com.resumeiq.jobdescription;

import com.resumeiq.common.domain.PublicIdEntity;
import com.resumeiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

/**
 * A target job description, pasted by the user.
 *
 * <p>A job description is stored rather than passed straight through to the AI because the
 * scores only mean something next to the text they were computed against. Re-reading an
 * analysis from six weeks ago and seeing which posting it was measured against is the whole
 * point of the history feature.
 *
 * <p>{@code contentHash} exists so the same posting analysed twice is the same row. People
 * paste the same description repeatedly while iterating on a resume — that is the core loop of
 * this product — and without a hash each pass would duplicate several kilobytes of text and
 * make "my scores over time for this job" impossible to ask for. The unique constraint is
 * scoped per user, not global: two people applying to the same job must not be able to detect
 * each other through a shared row.
 */
@Entity
@Table(
        name = "job_descriptions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_job_descriptions_user_hash",
                columnNames = {"user_id", "content_hash"}
        ),
        indexes = @Index(name = "ix_job_descriptions_user_created", columnList = "user_id, created_at")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class JobDescription extends PublicIdEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_job_descriptions_user")
    )
    private User user;

    /** Role title. Required, because it is how the user recognises this row in a list. */
    @Column(name = "title", nullable = false, length = 160)
    private String title;

    @Column(name = "company", length = 160)
    private String company;

    @Lob
    @Column(name = "raw_text", nullable = false)
    private String rawText;

    /** SHA-256 of the normalised text, hex encoded — always 64 characters. */
    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    /**
     * Fingerprints a job description for de-duplication.
     *
     * <p>Whitespace is collapsed and case folded first, so a re-paste that picked up different
     * line breaks from the browser still matches. This is a de-duplication key, not a security
     * primitive — SHA-256 is used because it is available and collision-free in practice, not
     * to protect anything.
     */
    public static String hashOf(String text) {
        String normalized = text == null
                ? ""
                : text.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            // Every JVM is required to ship SHA-256, so this cannot happen at runtime.
            throw new IllegalStateException("SHA-256 is not available in this JVM", ex);
        }
    }
}

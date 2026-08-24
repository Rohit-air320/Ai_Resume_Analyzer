package com.resumeiq.resume;

import com.resumeiq.common.domain.PublicIdEntity;
import com.resumeiq.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * An uploaded resume: the stored file, the text pulled out of it, and nothing else.
 *
 * <p>Three privacy rules from the spec are visible in this mapping.
 *
 * <p>First, {@code originalFilename} is kept only to show the person what they uploaded, and
 * {@code storageKey} — a generated name — is what the file is actually saved as. Saving under
 * a user-supplied name is how {@code ../../etc/passwd} ends up written somewhere it should not
 * be, and how one user's upload overwrites another's.
 *
 * <p>Second, {@code extractedText} is a {@code @Lob}. It is the most sensitive column in the
 * schema, so no list query may return it: list endpoints read the {@link ResumeSummary}
 * projection instead, which cannot see the field at all. That is a stronger guarantee than
 * remembering to leave it out of a DTO.
 *
 * <p>Third, deletion is a real delete. A {@code deletedAt} flag would be more convenient for
 * undo, but "delete uploaded files when requested" means the row and the file go away.
 */
@Entity
@Table(
        name = "resumes",
        indexes = {
                @Index(name = "ix_resumes_user_created", columnList = "user_id, created_at"),
                @Index(name = "ix_resumes_storage_key", columnList = "storage_key", unique = true)
        }
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Resume extends PublicIdEntity {

    /**
     * Owner. Lazy, like every {@code @ManyToOne} here: the default is eager, which would fetch
     * the whole account row every time a resume is loaded for any reason.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_resumes_user")
    )
    private User user;

    /** What the person calls this resume in the UI. Defaults to the uploaded file's name. */
    @Column(name = "label", nullable = false, length = 140)
    private String label;

    /** Display only. Never used to build a path — see {@link #storageKey}. */
    @Column(name = "original_filename", nullable = false, length = 255)
    private String originalFilename;

    /** Server-generated storage name, unique across all users. */
    @Column(name = "storage_key", nullable = false, length = 200)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "file_size_bytes", nullable = false)
    private long fileSizeBytes;

    /** Null until extraction succeeds. Reported in the UI as a resume-length sanity check. */
    @Column(name = "page_count")
    private Integer pageCount;

    @Column(name = "word_count")
    private Integer wordCount;

    /**
     * Plain text of the resume. {@code @Lob} maps to {@code LONGTEXT} on MySQL and {@code CLOB}
     * on H2, so a long resume is not silently truncated at 255 characters.
     */
    @Lob
    @Column(name = "extracted_text")
    private String extractedText;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private ResumeStatus status;

    /**
     * Why extraction failed, phrased for the person who uploaded the file. Bounded length
     * because it is written from a caught exception, and an exception message is not a
     * suitable place to put an unbounded column.
     */
    @Column(name = "extraction_error", length = 300)
    private String extractionError;

    /** True when this resume can be analysed. Phase 7 refuses to start an analysis otherwise. */
    public boolean isAnalysable() {
        return status == ResumeStatus.TEXT_EXTRACTED
                && extractedText != null
                && !extractedText.isBlank();
    }
}

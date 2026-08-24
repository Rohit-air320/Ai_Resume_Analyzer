package com.resumeiq.user;

import com.resumeiq.common.domain.PublicIdEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.Locale;

/**
 * An account. Everything else in the schema hangs off this row.
 *
 * <p>Table is {@code users}, not {@code user}: {@code USER} is a reserved word in MySQL 8 and
 * an unquoted {@code select * from user} would fail.
 *
 * <p>There is deliberately no {@code @OneToMany} to resumes or analyses. Mapping them would
 * invite {@code user.getAnalyses()} — an unbounded collection that grows for the lifetime of
 * the account and would be loaded in full to answer "show me the last five". Those directions
 * are queries, not fields, and live on the repositories.
 */
@Entity
@Table(
        name = "users",
        uniqueConstraints = @UniqueConstraint(name = "uk_users_email", columnNames = "email")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class User extends PublicIdEntity {

    /**
     * Login identity, always stored normalised — see {@link #setEmail(String)}.
     *
     * <p>Normalising in Java rather than relying on the column collation is a portability
     * decision: MySQL's default collation compares case-insensitively, H2's does not, and a
     * uniqueness rule that holds in production but not in tests is worse than no rule.
     */
    @Column(name = "email", nullable = false, length = 180)
    private String email;

    /**
     * BCrypt hash, never a password. 100 characters leaves room above BCrypt's 60 for a future
     * algorithm change without a migration.
     */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 120)
    private String fullName;

    /** The role the person is aiming for. Used as a default when starting a new analysis. */
    @Column(name = "target_role", length = 120)
    private String targetRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", length = 20)
    private ExperienceLevel experienceLevel;

    /**
     * Stored as a string, like every enum here. Ordinals would silently remap every existing
     * row the first time someone inserts a constant into the middle of the enum.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 20)
    private Role role;

    /** Updated on successful login in Phase 3. Null until the first one. */
    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Trims and lower-cases, so lookups and the unique constraint agree on what is equal. */
    public void setEmail(String email) {
        this.email = normalizeEmail(email);
    }

    /**
     * Belt and braces for the normalisation rule.
     *
     * <p>{@link #setEmail} and {@link #register} both normalise, but Lombok's builder writes
     * straight to the field and bypasses them. Enforcing it again at the persistence boundary
     * means no construction path — present or future — can put a mixed-case address in the
     * column and quietly defeat the unique constraint.
     */
    @PrePersist
    @PreUpdate
    void normalizeEmailBeforeWrite() {
        this.email = normalizeEmail(this.email);
    }

    /**
     * The one place that decides what two email addresses being "the same" means.
     *
     * <p>{@link Locale#ROOT} is not incidental: under a Turkish locale {@code "I"} lower-cases
     * to a dotless {@code "ı"}, which would let the same address register twice.
     */
    public static String normalizeEmail(String raw) {
        return raw == null ? null : raw.trim().toLowerCase(Locale.ROOT);
    }

    /** Factory for registration, so the two invariants of a new account live in one place. */
    public static User register(String email, String passwordHash, String fullName) {
        return User.builder()
                .email(normalizeEmail(email))
                .passwordHash(passwordHash)
                .fullName(fullName)
                .role(Role.USER)
                .build();
    }
}

package com.resumeiq.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Adds the identifier the outside world is allowed to see.
 *
 * <p>Rows that a browser can address carry two ids on purpose:
 * <ul>
 *   <li>{@code id}, a {@code BIGINT} the database uses for joins and foreign keys, where a
 *       narrow monotonic key keeps the clustered index compact;</li>
 *   <li>{@code publicId}, a random UUID that appears in URLs, DTOs and logs.</li>
 * </ul>
 *
 * <p>The reason is the spec's own rule that user-provided ids must never be trusted.
 * Ownership is still checked on every read — see the {@code findByPublicIdAndUserId} methods
 * on the repositories — but a sequential id additionally lets anyone walk
 * {@code /api/analyses/1,2,3...} to learn how many analyses the whole system holds, and turns
 * every log line into a guessable handle. A UUID removes that class of problem entirely and
 * costs one indexed column.
 *
 * <p>Stored as {@code char(36)} rather than Hibernate's default {@code binary(16)} on MySQL.
 * Sixteen bytes would be smaller, but these tables are small and a readable id you can copy
 * out of a log line straight into a {@code WHERE} clause is worth more than the space.
 */
@MappedSuperclass
@Getter
public abstract class PublicIdEntity extends BaseEntity {

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "public_id", nullable = false, unique = true, updatable = false, length = 36)
    private UUID publicId = UUID.randomUUID();
}

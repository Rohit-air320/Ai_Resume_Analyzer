package com.resumeiq.common.domain;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Getter;
import org.hibernate.Hibernate;

import java.time.Instant;

/**
 * Surrogate key and audit timestamps for every entity in the system.
 *
 * <p>Timestamps come from JPA lifecycle callbacks rather than Spring Data's
 * {@code @CreatedDate}/{@code @LastModifiedDate}. Those need {@code @EnableJpaAuditing} on a
 * {@code @Configuration} class, and a {@code @DataJpaTest} slice does not pick configuration
 * classes up — so auditing silently does nothing in repository tests and every entity
 * arrives with a null {@code createdAt}. Callbacks are part of JPA itself, so they fire in
 * every slice, in production, and in a plain {@code EntityManager} test alike. The moment we
 * need {@code createdBy} we will need Spring Data auditing and an {@code AuditorAware}, and
 * that is the moment to switch.
 *
 * <p>{@code equals}/{@code hashCode} follow the rules that keep entities safe inside
 * collections:
 * <ul>
 *   <li>{@link Hibernate#getClass} unwraps lazy proxies, so a proxy and its loaded entity
 *       compare equal and two different entity types with the same id do not.</li>
 *   <li>A transient entity (null id) equals only itself, so two unsaved rows never collapse
 *       into one set element.</li>
 *   <li>{@code hashCode} is derived from the type, not the id. An id-based hash changes when
 *       the entity is persisted, which would leave it unfindable in a {@code HashSet} it had
 *       already been added to.</li>
 * </ul>
 */
@MappedSuperclass
@Getter
public abstract class BaseEntity {

    /**
     * Internal identifier. It is a database concern: it appears in foreign keys and joins,
     * never in a URL or an API response — see {@link PublicIdEntity}.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onInsert() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    /** True once the row has been persisted, which is also what makes equality meaningful. */
    public boolean isPersisted() {
        return id != null;
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null || !Hibernate.getClass(this).equals(Hibernate.getClass(other))) {
            return false;
        }
        BaseEntity that = (BaseEntity) other;
        return id != null && id.equals(that.id);
    }

    @Override
    public final int hashCode() {
        return Hibernate.getClass(this).hashCode();
    }
}

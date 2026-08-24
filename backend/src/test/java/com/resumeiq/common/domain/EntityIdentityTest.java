package com.resumeiq.common.domain;

import com.resumeiq.resume.Resume;
import com.resumeiq.skill.Skill;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import com.resumeiq.user.User;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The identity contract every entity inherits.
 *
 * <p>These are the three behaviours that {@link BaseEntity} and {@link PublicIdEntity} exist to
 * provide, and all three are the kind that break silently: nothing throws, the data is just
 * quietly wrong later.
 */
@RepositoryTest
class EntityIdentityTest {

    @Autowired
    private TestEntityManager em;

    private static Skill skill(String slug) {
        return Skill.builder()
                .slug(slug)
                .displayName(slug)
                .category(SkillCategory.CONCEPT)
                .build();
    }

    @Test
    @DisplayName("an entity stays findable in a HashSet after it is persisted")
    void hashCodeSurvivesPersisting() {
        Skill unsaved = skill("identity-probe-one");
        Set<Skill> set = new HashSet<>();
        set.add(unsaved);

        em.persistAndFlush(unsaved);

        // With an id-based hashCode this fails: the hash changes when the id is assigned, so the
        // object lands in the wrong bucket and the set it is already a member of cannot find it.
        assertThat(unsaved.getId()).isNotNull();
        assertThat(set).contains(unsaved);
    }

    @Test
    @DisplayName("two unsaved entities of the same type are not equal")
    void unsavedEntitiesAreDistinct() {
        Skill first = skill("identity-probe-two");
        Skill second = skill("identity-probe-three");

        // Both ids are null. Treating null == null as equality would collapse every new entity
        // into one member of any set they are added to.
        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(first);
    }

    @Test
    @DisplayName("equals sees through a lazy proxy")
    void equalsUnwrapsProxies() {
        User owner = em.persistFlushFind(TestFixtures.user("identity@example.com"));
        Resume resume = em.persistFlushFind(TestFixtures.resume(owner, "Identity CV"));
        em.clear();

        User proxy = em.find(Resume.class, resume.getId()).getUser();

        // The class check is the real assertion: getClass() on the proxy is a generated subclass,
        // so an equals() written with getClass() comparison would report a mismatch between an
        // entity and its own proxy.
        assertThat(proxy.getClass()).isNotEqualTo(User.class);
        assertThat(Hibernate.getClass(proxy)).isEqualTo(User.class);
        assertThat(proxy).isEqualTo(em.find(User.class, owner.getId()));
    }

    @Test
    @DisplayName("lifecycle callbacks stamp the timestamps and never rewrite created_at")
    void callbacksMaintainTimestamps() {
        Skill saved = em.persistFlushFind(skill("identity-probe-four"));

        Instant createdAt = saved.getCreatedAt();
        assertThat(createdAt).isNotNull();
        assertThat(saved.getUpdatedAt()).isEqualTo(createdAt);

        saved.setDisplayName("Renamed");
        em.flush();

        assertThat(saved.getCreatedAt()).isEqualTo(createdAt);
        assertThat(saved.getUpdatedAt()).isAfterOrEqualTo(createdAt);
    }

    @Test
    @DisplayName("a public id is assigned before insert and is unique per row")
    void publicIdIsIndependentOfThePrimaryKey() {
        User owner = em.persistFlushFind(TestFixtures.user("publicid@example.com"));
        Resume resume = TestFixtures.resume(owner, "Public Id CV");

        UUID beforeInsert = resume.getPublicId();
        assertThat(beforeInsert).isNotNull();

        em.persistAndFlush(resume);
        Resume second = em.persistFlushFind(TestFixtures.resume(owner, "Second CV"));

        // The value is generated in Java before the insert and is not touched by it, so it can be
        // logged and put in URLs while the sequential primary key stays private.
        assertThat(resume.getPublicId()).isEqualTo(beforeInsert);
        assertThat(second.getPublicId()).isNotEqualTo(resume.getPublicId());
        assertThat(second.getId()).isEqualTo(resume.getId() + 1);
    }
}

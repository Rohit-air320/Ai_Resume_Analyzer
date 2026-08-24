package com.resumeiq.skill;

import com.resumeiq.common.domain.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * A canonical skill in the shared taxonomy.
 *
 * <p>This is the entity that makes the whole feature work, and the reason the schema is
 * relational. An AI response is free text: one run says "Spring Boot", the next says
 * "springboot", a job description says "Spring-Boot". Stored verbatim, those are three
 * different skills, and "which skills am I missing most often?" — the question the skill-gap
 * page exists to answer — becomes unanswerable. Every mention is therefore resolved to one row
 * here through {@link #slug} or {@link #aliases} before it is counted.
 *
 * <p>Reference data, not user data: there is no {@code publicId} and no owner, because the
 * taxonomy is the same for everyone and is addressed by its slug.
 */
@Entity
@Table(
        name = "skills",
        uniqueConstraints = @UniqueConstraint(name = "uk_skills_slug", columnNames = "slug"),
        indexes = @Index(name = "ix_skills_category", columnList = "category")
)
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Skill extends BaseEntity {

    /** Lookup key: lower case, hyphenated, no punctuation. {@code "spring-boot"}. */
    @Column(name = "slug", nullable = false, length = 80)
    private String slug;

    /** How the skill is written in the UI, with the capitalisation people expect. */
    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    private SkillCategory category;

    /**
     * Every other spelling that means this skill.
     *
     * <p>An {@code @ElementCollection} rather than an entity because an alias has no identity
     * of its own and is never queried except through its skill. The unique constraint is on the
     * alias alone, not on {@code (skill_id, alias)}: one string must not resolve to two
     * different skills, or canonicalisation stops being deterministic.
     */
    @ElementCollection
    @CollectionTable(
            name = "skill_aliases",
            joinColumns = @JoinColumn(
                    name = "skill_id",
                    foreignKey = @ForeignKey(name = "fk_skill_aliases_skill")
            ),
            uniqueConstraints = @UniqueConstraint(name = "uk_skill_aliases_alias", columnNames = "alias")
    )
    @Column(name = "alias", nullable = false, length = 80)
    @Builder.Default
    private Set<String> aliases = new LinkedHashSet<>();

    /**
     * Turns any spelling of a skill into a slug.
     *
     * <p>Two deliberate special cases, both of which are real skills that a naive
     * "strip punctuation" rule gets wrong. {@code +} and {@code #} are spelled out, because
     * "C++" and "C#" would otherwise both collapse to {@code "c"} and a C# developer would be
     * told they are missing C++. A leading dot becomes {@code "dot"}, so ".NET" is
     * {@code "dotnet"} rather than {@code "net"} — while an interior dot stays a separator, so
     * "Node.js" is {@code "node-js"}.
     */
    public static String slugify(String raw) {
        if (raw == null) {
            return null;
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT)
                .replace("+", "plus")
                .replace("#", "sharp");
        if (lowered.startsWith(".")) {
            lowered = "dot" + lowered.substring(1);
        }
        String hyphenated = lowered.replaceAll("[^a-z0-9]+", "-");
        return hyphenated.replaceAll("(^-+)|(-+$)", "");
    }

    /** Adds an alias in normalised form. Ignores a value equal to this skill's own slug. */
    public void addAlias(String alias) {
        String normalized = slugify(alias);
        if (normalized != null && !normalized.isEmpty() && !normalized.equals(slug)) {
            aliases.add(normalized);
        }
    }
}

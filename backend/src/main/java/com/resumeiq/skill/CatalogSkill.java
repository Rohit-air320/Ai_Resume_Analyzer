package com.resumeiq.skill;

/**
 * A skill from the catalogue, detached from JPA.
 *
 * <p>The matcher runs outside a transaction and holds its index for the length of a request, which
 * makes carrying {@link Skill} entities around a hazard rather than a convenience: an entity's
 * aliases are a lazy {@code @ElementCollection}, and touching one after its session closed throws.
 * Copying the three fields anyone actually needs removes the whole class of problem, and makes the
 * matcher testable with a list of records instead of a database.
 *
 * @param slug        canonical lookup key
 * @param displayName how the skill is written in the UI
 * @param category    what kind of skill it is, used to group the results
 */
public record CatalogSkill(String slug, String displayName, SkillCategory category) {

    /** Copies a loaded entity. The caller is responsible for having fetched it. */
    public static CatalogSkill of(Skill skill) {
        return new CatalogSkill(skill.getSlug(), skill.getDisplayName(), skill.getCategory());
    }
}

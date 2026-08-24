package com.resumeiq.skill;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.config.ResumeIqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Loads the skill taxonomy from {@code resources/data/skills.json} at startup.
 *
 * <p>Three decisions worth explaining.
 *
 * <p><b>A runner, not {@code data.sql}.</b> Boot's SQL script runs on every start and would
 * either duplicate rows or need {@code INSERT IGNORE}, which is MySQL-only. This is plain Java,
 * so it works identically against H2 in development and MySQL in production, and — more
 * importantly — it can be unit tested.
 *
 * <p><b>Idempotent by slug.</b> Existing skills are left alone and only genuinely new ones are
 * inserted, so restarting the app is free and the catalogue file can be extended over time
 * without a migration. Aliases are merged into existing skills, because that is how the taxonomy
 * improves: a spelling the AI produced last week gets added to the JSON and starts resolving.
 *
 * <p><b>Loud on a bad file.</b> A malformed catalogue fails startup. It is a packaged resource,
 * so a broken one is a build mistake, and the alternative — booting with an empty taxonomy — is
 * a silent, quiet degradation of every analysis the app then produces.
 */
@Component
public class SkillCatalogSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillCatalogSeeder.class);
    private static final String CATALOG_RESOURCE = "data/skills.json";

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;
    private final boolean enabled;

    public SkillCatalogSeeder(
            SkillRepository skillRepository,
            ObjectMapper objectMapper,
            ResumeIqProperties properties) {
        this.skillRepository = skillRepository;
        this.objectMapper = objectMapper;
        this.enabled = properties.seed().skills();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!enabled) {
            log.info("Skill catalogue seeding is disabled (resumeiq.seed.skills=false)");
            return;
        }
        SeedResult result = seed();
        log.info("Skill catalogue: {} skill(s) inserted, {} alias(es) added, {} already present",
                result.skillsInserted(), result.aliasesAdded(), result.skillsSkipped());
    }

    /**
     * Applies the catalogue to the database and reports what changed.
     *
     * <p>Separate from {@link #run} and public so a test can call it twice and assert that the
     * second call is a no-op — which is the only way to actually prove idempotency.
     */
    @Transactional
    public SeedResult seed() {
        List<SkillDefinition> definitions = readCatalog();
        Set<String> takenAliases = new HashSet<>(skillRepository.findAllAliases());

        int inserted = 0;
        int skipped = 0;
        int aliasesAdded = 0;

        for (SkillDefinition definition : definitions) {
            String slug = Skill.slugify(definition.name());
            if (slug == null || slug.isEmpty()) {
                throw new IllegalStateException(
                        "Skill catalogue contains an entry with no usable name: " + definition.name());
            }

            Skill skill = skillRepository.findBySlug(slug).orElse(null);
            if (skill == null) {
                skill = Skill.builder()
                        .slug(slug)
                        .displayName(definition.name())
                        .category(definition.category())
                        .build();
                inserted++;
            } else {
                skipped++;
            }

            for (String alias : definition.safeAliases()) {
                String normalized = Skill.slugify(alias);
                if (normalized == null || normalized.isEmpty() || normalized.equals(slug)) {
                    continue;
                }
                if (skill.getAliases().contains(normalized)) {
                    continue;
                }
                if (takenAliases.contains(normalized)) {
                    // Two skills claiming one spelling would make resolution non-deterministic:
                    // whichever row the query happened to return first would win.
                    log.warn("Alias '{}' is already mapped to another skill; not adding it to {}",
                            normalized, slug);
                    continue;
                }
                skill.addAlias(normalized);
                takenAliases.add(normalized);
                aliasesAdded++;
            }

            skillRepository.save(skill);
        }

        return new SeedResult(inserted, skipped, aliasesAdded);
    }

    private List<SkillDefinition> readCatalog() {
        ClassPathResource resource = new ClassPathResource(CATALOG_RESOURCE);
        try (InputStream input = resource.getInputStream()) {
            List<SkillDefinition> definitions =
                    objectMapper.readValue(input, new TypeReference<List<SkillDefinition>>() {
                    });
            if (definitions == null || definitions.isEmpty()) {
                throw new IllegalStateException(CATALOG_RESOURCE + " contains no skills");
            }
            return definitions;
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read the skill catalogue " + CATALOG_RESOURCE, ex);
        }
    }

    /** One entry in {@code skills.json}. The slug is derived, never written by hand. */
    public record SkillDefinition(String name, SkillCategory category, List<String> aliases) {

        List<String> safeAliases() {
            return aliases == null ? List.of() : aliases;
        }
    }

    /** What one seeding pass changed. */
    public record SeedResult(int skillsInserted, int skillsSkipped, int aliasesAdded) {

        public boolean changedNothing() {
            return skillsInserted == 0 && aliasesAdded == 0;
        }
    }
}

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
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
 *
 * <p><b>One transaction, one read.</b> Both {@link #run} and {@link #seed} carry
 * {@code @Transactional}, which looks redundant and is not: {@code run} calls {@code seed} on
 * {@code this}, and a self-invocation never passes through the proxy that applies the annotation.
 * Without the mark on {@code run}, the startup path had no transaction at all — which a fresh
 * database hides completely and a second start against a populated one does not, because every
 * skill then comes back detached and reading its lazy alias set throws. The catalogue is also read
 * in a single query with aliases fetched, rather than one lookup per entry, so a start costs one
 * select instead of a hundred and thirty.
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
    @Transactional
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

        // One query, aliases included. A lookup per entry was a hundred and thirty round trips at
        // every start, and — the part that actually broke — each row came back detached whenever
        // the caller had no transaction, so the alias check below threw LazyInitializationException.
        Map<String, Skill> existing = skillRepository.findAllWithAliases().stream()
                .collect(Collectors.toMap(Skill::getSlug, skill -> skill));
        Set<String> takenAliases = existing.values().stream()
                .flatMap(skill -> skill.getAliases().stream())
                .collect(Collectors.toCollection(HashSet::new));

        int inserted = 0;
        int skipped = 0;
        int aliasesAdded = 0;

        for (SkillDefinition definition : definitions) {
            String slug = Skill.slugify(definition.name());
            if (slug == null || slug.isEmpty()) {
                throw new IllegalStateException(
                        "Skill catalogue contains an entry with no usable name: " + definition.name());
            }

            Skill skill = existing.get(slug);
            if (skill == null) {
                skill = Skill.builder()
                        .slug(slug)
                        .displayName(definition.name())
                        .category(definition.category())
                        .build();
                // Into the map as well, so two catalogue entries that slugify the same way behave
                // as they did when this read the database per entry: the second is a skip, not a
                // duplicate insert that trips the unique index.
                existing.put(slug, skill);
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

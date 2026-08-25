package com.resumeiq.jobdescription.parse;

import com.resumeiq.skill.CatalogSkill;
import com.resumeiq.skill.SkillIndex;
import com.resumeiq.skill.SkillScan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds the catalogue skills a posting asks for.
 *
 * <h2>What this class adds</h2>
 *
 * <p>Locating the skills is {@link SkillScan}'s job, and it is shared with the resume side on
 * purpose: two scanners that tokenised differently would produce gaps that are really parser
 * disagreements. What is left here is the part that only a posting has — <em>importance</em>. The
 * same word means different things under "Requirements" and under "Nice to have", and turning a
 * list of sightings into that judgement is what this class does.
 *
 * <h2>What it does not do</h2>
 *
 * <p>Only catalogue skills come back. A posting asking for something the taxonomy has never heard
 * of produces nothing here — deliberately, because an unknown term cannot be compared against a
 * resume, aggregated across analyses, or shown on the skill-gap page, and inventing a skill row
 * for every unrecognised noun would fill that page with words like "stakeholders". Terms the
 * catalogue does not know are still surfaced, as keywords, by {@link KeywordExtractor}.
 */
public final class SkillMatcher {

    private SkillMatcher() {
    }

    /**
     * Detects skills across every block of a posting.
     *
     * @param blocks the posting, already split into sections
     * @param index  the catalogue
     * @return skills ordered by importance, then by how often they appear, then by name. Stable,
     *         because this ordering reaches the API and an endpoint whose list order changes
     *         between identical requests is one nobody can test.
     */
    public static List<DetectedSkill> detect(List<PostingBlock> blocks, SkillIndex index) {
        Map<String, Match> matches = new LinkedHashMap<>();
        for (PostingBlock block : blocks) {
            for (CatalogSkill hit : SkillScan.hits(block.text(), index)) {
                matches.computeIfAbsent(hit.slug(), slug -> new Match(hit)).add(block);
            }
        }
        List<DetectedSkill> detected = new ArrayList<>(matches.size());
        for (Match match : matches.values()) {
            detected.add(match.toDetectedSkill());
        }
        detected.sort(Comparator
                .comparingInt((DetectedSkill skill) -> -skill.importance().weight())
                .thenComparingInt(skill -> -skill.mentions())
                .thenComparing(DetectedSkill::displayName));
        return List.copyOf(detected);
    }

    /**
     * One skill's running tally. Mutable and package-private to this file because accumulating
     * into an immutable record would mean rebuilding it on every mention, and a posting has
     * thousands of tokens.
     */
    private static final class Match {

        private final CatalogSkill skill;
        private int mentions;

        // Overwritten by the first sighting, always. They are initialised anyway so that no
        // field here can be null, and so a future caller that builds a Match without adding to
        // it gets a harmless answer rather than a NullPointerException.
        private SkillImportance importance = SkillImportance.MENTIONED;
        private PostingSection strongestSection = PostingSection.OTHER;
        private String foundUnder;

        private Match(CatalogSkill skill) {
            this.skill = skill;
        }

        /**
         * Folds in one more sighting. The strongest section wins, so a skill under both
         * "Requirements" and "Nice to have" is required — it was required somewhere, and telling
         * someone a hard requirement is optional is the more expensive mistake.
         *
         * <p>The first sighting always records itself, which is not the same as "strictly
         * stronger wins" and is the bug this shape fixes. With a strict comparison against a
         * {@code MENTIONED} starting value, a skill seen only in a section that means
         * {@code MENTIONED} — under a "Perks" heading, say — never got past the comparison, so it
         * was reported with no {@code foundUnder} and a section of {@code OTHER}: the evidence
         * for the one reading that most needs explaining was silently dropped.
         */
        private void add(PostingBlock block) {
            mentions++;
            SkillImportance candidate = block.importance();
            if (mentions == 1 || candidate.weight() > importance.weight()) {
                importance = candidate;
                strongestSection = block.section();
                foundUnder = block.heading();
            }
        }

        private DetectedSkill toDetectedSkill() {
            return new DetectedSkill(skill.slug(), skill.displayName(), skill.category(),
                    importance, mentions, strongestSection, foundUnder);
        }
    }
}

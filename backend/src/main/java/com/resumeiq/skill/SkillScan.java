package com.resumeiq.skill;

import com.resumeiq.common.text.TextTokenizer;
import com.resumeiq.common.text.TextTokenizer.Token;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Finds catalogue skills in a piece of text: longest match, left to right, whole tokens only.
 *
 * <p>This is the one piece of code that decides what "the text mentions Spring Boot" means, and it
 * is shared because both halves of an analysis have to agree on it. A posting is scanned for what
 * it asks for and a resume is scanned for what it claims, and those two answers are then compared
 * skill by skill — so if the two scans disagreed about tokenisation, the comparison would report
 * gaps that are really parser differences. "Your resume is missing Spring Boot" had better not mean
 * "the resume scanner and the posting scanner read that phrase differently".
 *
 * <h2>The three problems the rule solves</h2>
 *
 * <ul>
 *   <li>"Spring Boot" is Spring Boot, and is not also counted as Spring. A Spring Boot posting
 *       should not report two skills where a reader sees one.</li>
 *   <li>"Amazon Web Services" beats "Amazon", for the same reason.</li>
 *   <li>"JavaScript" is not Java. That one is not about longest match at all — it falls out of
 *       comparing whole tokens instead of searching for substrings, which is the deeper reason this
 *       tokenises rather than calling {@code contains}. Substring matching is how a JavaScript
 *       developer gets told they know Java.</li>
 * </ul>
 *
 * <h2>What comes back</h2>
 *
 * <p>Every sighting, in the order it was found, repeats included. Counting is the caller's job
 * because the two callers count differently: a posting tallies mentions and escalates importance by
 * the section a skill appeared under, while a resume tallies mentions and records which sections
 * they were in. Returning a set here would have thrown away the one thing both of them need.
 *
 * <p>Only catalogue skills come back. A term the taxonomy has never heard of produces nothing —
 * deliberately, because an unknown term cannot be compared across two documents, aggregated across
 * analyses, or shown on the skill-gap page. Unknown terms are still surfaced, as keywords.
 */
public final class SkillScan {

    private SkillScan() {
    }

    /**
     * Every catalogue skill sighted in {@code text}.
     *
     * @param text  any prose. Sentence and line splitting happens here, so callers do not have to
     *              agree on where a phrase may span
     * @param index the catalogue
     * @return sightings in encounter order, with one entry per occurrence
     */
    public static List<CatalogSkill> hits(String text, SkillIndex index) {
        if (text == null || text.isBlank() || index.size() == 0) {
            return List.of();
        }
        List<CatalogSkill> found = new ArrayList<>();
        for (String segment : TextTokenizer.segments(text)) {
            scan(TextTokenizer.tokens(segment), index, found);
        }
        return found;
    }

    /**
     * Walks one segment, consuming the widest term the catalogue knows at each position.
     *
     * <p>A hit consumes the words it matched, which is what stops "Spring Boot" from also counting
     * as "Spring". A miss advances by one token, so a term that begins mid-phrase is still found.
     */
    private static void scan(List<Token> tokens, SkillIndex index, List<CatalogSkill> found) {
        int position = 0;
        while (position < tokens.size()) {
            int widest = Math.min(index.maxTermWords(), tokens.size() - position);
            int consumed = 1;
            for (int width = widest; width >= 1; width--) {
                String key = TextTokenizer.keyOf(tokens, position, width);
                Optional<CatalogSkill> hit = index.find(key, tokens.get(position).original());
                if (hit.isPresent()) {
                    found.add(hit.get());
                    consumed = width;
                    break;
                }
            }
            position += consumed;
        }
    }
}

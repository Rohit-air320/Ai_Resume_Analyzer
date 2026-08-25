package com.resumeiq.jobdescription.parse;

import com.resumeiq.common.text.PlainText;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.skill.SkillIndex;
import com.resumeiq.skill.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Reads a pasted job posting into {@link PostingInsight}.
 *
 * <p>The only Spring bean in this package. Everything it calls is static and pure —
 * {@link SectionSplitter}, {@link SkillMatcher}, {@link KeywordExtractor},
 * {@link ExperienceDemand} — which is what makes the parser testable without a database, a context,
 * or a mock: each of those can be handed a string and checked against an expected answer. This class
 * exists to supply the one thing they cannot compute for themselves, the skill catalogue, and to
 * decide the order.
 *
 * <h2>Nothing is cached</h2>
 *
 * <p>The catalogue is queried per parse. That is a few hundred rows against a local index, next to
 * text processing that costs more, so the saving would be invisible — and the cost of caching is
 * not: a cached catalogue means the skill added on Tuesday is not detected until the next restart,
 * which is exactly the sort of "works on my machine, stale in production" behaviour that is
 * miserable to diagnose. When the catalogue reaches a size where this matters, the fix is a
 * {@code @Cacheable} annotation on the repository method and a documented eviction — not a private
 * field that silently goes stale.
 *
 * <h2>A posting with no headings</h2>
 *
 * <p>Plenty of postings are one long paragraph, and plenty more lose their formatting on the way
 * through the clipboard. When the split finds no section that asks for anything, the unheaded text
 * is re-read as requirements rather than left as {@code OTHER}, because someone who pastes a wall of
 * text still means "this is what the job needs" and a parser that answered "nothing is required
 * here" would be technically right and useless.
 *
 * <p>The important part is what that promotion does <em>not</em> touch:
 * {@link PostingInsight#sectionsFound()} is computed from the original split. So the insight can
 * report required skills while still saying the posting had no requirements heading, and the UI
 * never claims a heading existed that the user could scroll up and fail to find.
 */
@Component
public class JobPostingParser {

    private static final Logger log = LoggerFactory.getLogger(JobPostingParser.class);

    private final SkillRepository skills;
    private final ResumeIqProperties.Posting limits;

    public JobPostingParser(SkillRepository skills, ResumeIqProperties properties) {
        this.skills = skills;
        this.limits = properties.posting();
    }

    /**
     * Parses one posting.
     *
     * <p>Read-only and transactional because the catalogue query fetches a lazy
     * {@code @ElementCollection} of aliases; with {@code open-in-view} disabled, the join fetch has
     * to happen inside a transaction that is still open.
     *
     * @param rawText the posting as pasted. Normalised here rather than by the caller, so every
     *                route into the parser gets the same treatment
     * @param title   the role title, the one place seniority is reliably stated
     * @return what the posting asks for, or {@link PostingInsight#empty()} for text with nothing
     *         in it
     */
    @Transactional(readOnly = true)
    public PostingInsight parse(String rawText, String title) {
        return parseWith(rawText, title, SkillIndex.fromEntities(skills.findAllWithAliases()),
                limits.maxKeywords());
    }

    /**
     * The same parse, against a catalogue the caller already has.
     *
     * <p>This overload exists for the analysis service, which needs the catalogue for the resume
     * side as well. Without it, one analysis would load the skill table twice and — worse — could
     * in principle load two different versions of it, so a skill added between the two queries
     * would be required by the posting and unmatched in the resume. One catalogue per analysis is
     * not an optimisation, it is what makes the comparison coherent.
     *
     * <p>Static and free of Spring, so the whole pipeline can be exercised with
     * {@link SkillIndex#of} and a string.
     *
     * @param rawText     the posting as pasted
     * @param title       the role title, the one place seniority is reliably stated
     * @param index       the catalogue
     * @param maxKeywords how many ranked keywords to keep
     */
    public static PostingInsight parseWith(String rawText, String title, SkillIndex index,
                                           int maxKeywords) {
        String text = PlainText.normalise(rawText);
        if (text == null || text.isBlank()) {
            return PostingInsight.empty();
        }

        List<PostingBlock> split = SectionSplitter.split(text);
        Set<PostingSection> sectionsFound = headedSections(split);
        boolean structured = split.stream().anyMatch(block -> block.section().isDemanding());
        List<PostingBlock> blocks = structured ? split : readAsRequirements(split);

        List<DetectedSkill> detected = SkillMatcher.detect(blocks, index);
        List<Keyword> keywords = KeywordExtractor.extract(blocks, detected, maxKeywords);
        ExperienceDemand experience = ExperienceDemand.detect(blocks, title);

        // Counts only. The posting text itself is never logged: it is the user's data, it is often
        // pasted from an email, and a log line is the last place it should turn up.
        log.debug("Parsed posting: {} blocks, {} skills, {} keywords, structured={}",
                blocks.size(), detected.size(), keywords.size(), structured);

        return new PostingInsight(detected, keywords, experience,
                PlainText.countWords(text), sectionsFound, structured);
    }

    /**
     * Which section kinds the posting's own headings named.
     *
     * <p>{@link PostingSection#OTHER} is excluded on purpose. It is not a kind of section that was
     * found — it is what the splitter produces for text that had no heading at all, so reporting it
     * would turn "we recognised nothing here" into an entry in a list of things we recognised.
     */
    private static Set<PostingSection> headedSections(List<PostingBlock> blocks) {
        Set<PostingSection> found = EnumSet.noneOf(PostingSection.class);
        for (PostingBlock block : blocks) {
            if (block.section() != PostingSection.OTHER) {
                found.add(block.section());
            }
        }
        return found;
    }

    /**
     * Promotes unheaded text to requirements, leaving anything that was recognised alone.
     *
     * <p>Benefits and company blocks keep their own classification even here, so a posting whose
     * only heading was "Perks" does not end up recommending "insurance" as a keyword.
     */
    private static List<PostingBlock> readAsRequirements(List<PostingBlock> blocks) {
        List<PostingBlock> promoted = new ArrayList<>(blocks.size());
        for (PostingBlock block : blocks) {
            promoted.add(block.section() == PostingSection.OTHER
                    ? block.as(PostingSection.REQUIREMENTS)
                    : block);
        }
        return promoted;
    }
}

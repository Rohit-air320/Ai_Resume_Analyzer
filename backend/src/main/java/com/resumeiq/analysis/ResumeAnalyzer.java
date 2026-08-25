package com.resumeiq.analysis;

import com.resumeiq.analysis.ai.AdviceSanitiser;
import com.resumeiq.analysis.ai.AdviceSource;
import com.resumeiq.analysis.ai.AiAdvice;
import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.jobdescription.parse.JobPostingParser;
import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.skill.SkillIndex;
import com.resumeiq.skill.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Runs an analysis end to end.
 *
 * <p>The whole pipeline in one readable method: parse the posting, read the resume, compare them, score,
 * review the sections, then ask a writer for the words and validate what comes back. Phase 7's controller
 * calls this and persists the result; nothing about HTTP, authentication or JPA appears here.
 *
 * <h2>One catalogue load per analysis</h2>
 *
 * <p>The skill catalogue is read once and passed to both the posting parser and the resume reader. That is
 * not only a query saved — it guarantees both sides of the comparison were matched against the same
 * catalogue. Loading it twice would work today and would silently produce a wrong comparison the first
 * time a skill was added between the two reads.
 *
 * <h2>Everything below this is static</h2>
 *
 * <p>{@link #analyseWith} is pure, which is why the tests for this phase run in milliseconds with no
 * database, no Spring context and no network. The Spring-managed method exists only to load the catalogue
 * and read configuration. Keeping that boundary sharp is most of what makes the analysis testable.
 */
@Service
public class ResumeAnalyzer {

    private static final Logger log = LoggerFactory.getLogger(ResumeAnalyzer.class);

    private final SkillRepository skills;
    private final AdviceSource advice;
    private final ResumeIqProperties.Posting postingLimits;

    public ResumeAnalyzer(SkillRepository skills, AdviceSource advice,
                          ResumeIqProperties properties) {
        this.skills = skills;
        this.advice = advice;
        this.postingLimits = properties.posting();
    }

    /**
     * Analyses one resume against one posting.
     *
     * <p>Read-only despite being the product's central write path: this method computes, and Phase 7's
     * caller persists. Splitting it that way keeps the AI call — the slow, unreliable part — outside the
     * transaction that writes the result, so a provider timing out never holds a database transaction
     * open.
     */
    @Transactional(readOnly = true)
    public AnalysisOutcome analyse(AnalysisInput input) {
        SkillIndex catalogue = SkillIndex.fromEntities(skills.findAllWithAliases());
        AnalysisOutcome outcome = analyseWith(input, catalogue, postingLimits.maxKeywords(), advice);
        // Scores and counts only. No resume content, no posting content, no role title — the log is
        // where sensitive data leaks by accident, so nothing that came out of a document goes in it.
        log.info("Analysed a resume: overall {}, ats {}, jobMatch {} — {} skills demanded, {} gaps, "
                        + "advice from {}",
                outcome.facts().scores().overall(),
                outcome.facts().scores().ats(),
                outcome.facts().scores().jobMatch(),
                outcome.facts().skills().demanded().size(),
                outcome.facts().skills().gaps().size(),
                outcome.adviceSource());
        return outcome;
    }

    /**
     * The pipeline, with every dependency passed in.
     *
     * @param input       the two documents
     * @param catalogue   the skill catalogue, shared by both sides of the comparison
     * @param maxKeywords how many keywords the posting parser should rank
     * @param source      the writer to ask for the words
     */
    public static AnalysisOutcome analyseWith(AnalysisInput input, SkillIndex catalogue,
                                              int maxKeywords, AdviceSource source) {
        PostingInsight posting = JobPostingParser.parseWith(input.postingText(), input.roleTitle(),
                catalogue, maxKeywords);
        AnalysisFacts facts = AnalysisFacts.from(input.resumeText(), posting, input.roleTitle(),
                catalogue);

        // Sanitised here as well as inside the AI source. Belt and braces on purpose: this is the one
        // call every writer passes through, so it is the place that can guarantee no advice reaching
        // the database exceeds a column width or contradicts a finding — including advice from the
        // offline writer, which is code and can still be wrong.
        AiAdvice written = AdviceSanitiser.clean(source.adviseOn(facts, input.postingText()), facts);
        return new AnalysisOutcome(facts, written);
    }
}

package com.resumeiq.analysis.engine;

import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.skill.SkillIndex;

import java.util.List;

/**
 * Everything this backend knows for certain about one resume-and-posting pair.
 *
 * <p>The output of the deterministic half of the analysis, and the only thing the AI layer is given.
 * That boundary is the design: a language model never sees a score being decided, it sees the scores
 * and the findings that produced them and is asked to explain them and advise on them. So the numbers
 * are reproducible, the product works with no API key, and every claim in the advice can be traced
 * back to something in this record.
 *
 * <p>It is also the anti-hallucination mechanism, in a more practical sense than a prompt instruction.
 * The model cannot invent a missing skill, because the gap list is computed here and the sanitiser
 * drops anything the model names that is not in it. Telling a model not to make things up helps;
 * making its output structurally unable to introduce a new fact is what actually works.
 *
 * @param roleTitle the job title, used to address the advice to a specific role rather than to
 *                  "this position". Never trusted as a source of facts.
 * @param posting   the parsed posting
 * @param resume    the parsed resume. Carries the resume text, which is sent to the provider and
 *                  never logged or returned by the API.
 * @param skills    the skill-by-skill comparison
 * @param keywords  the keyword comparison, matched terms first
 * @param scores    the six scores and the notes behind them
 * @param sections  one review per resume section, in enum order
 */
public record AnalysisFacts(
        String roleTitle,
        PostingInsight posting,
        ResumeInsight resume,
        SkillComparison.Comparison skills,
        List<KeywordVerdict> keywords,
        ScoreCard scores,
        List<SectionReview> sections
) {

    public AnalysisFacts {
        keywords = List.copyOf(keywords);
        sections = List.copyOf(sections);
    }

    /**
     * Runs the whole deterministic pipeline.
     *
     * <p>Static and pure. Given the same two documents and the same skill catalogue it returns the
     * same facts, which is what makes the analysis testable without a database, a web server or a
     * network — and what makes a score somebody disputes something we can reproduce on demand.
     *
     * @param resumeText the extracted resume text
     * @param posting    the already-parsed posting
     * @param roleTitle  the job title
     * @param index      the skill catalogue, loaded once per analysis by the caller
     */
    public static AnalysisFacts from(String resumeText, PostingInsight posting, String roleTitle,
                                     SkillIndex index) {
        ResumeInsight resume = ResumeReader.read(resumeText, index);
        SkillComparison.Comparison skills = SkillComparison.compare(posting, resume);
        List<KeywordVerdict> keywords = KeywordComparison.compare(posting, resume);
        ScoreCard scores = ScoreEngine.score(posting, resume, skills, keywords);
        List<SectionReview> sections = SectionReviewer.review(resume, skills);
        return new AnalysisFacts(roleTitle, posting, resume, skills, keywords, scores, sections);
    }

    /** Terms the posting leans on that the resume does not use. What keyword advice is drawn from. */
    public List<KeywordVerdict> absentKeywords() {
        return keywords.stream().filter(verdict -> !verdict.isMatched()).toList();
    }

    /** Terms the resume already uses. Worth showing: it is the half of the picture that is working. */
    public List<KeywordVerdict> matchedKeywords() {
        return keywords.stream().filter(KeywordVerdict::isMatched).toList();
    }

    /**
     * True when there is too little here to advise on.
     *
     * <p>An empty resume or a posting with nothing readable in it. The analysis still completes and
     * still reports its scores — refusing would leave the user with nothing to act on — but the advice
     * layer uses this to keep its suggestions about the documents rather than pretending to compare
     * them.
     */
    public boolean isThin() {
        return resume.isEmpty() || (posting.skills().isEmpty() && posting.keywords().isEmpty());
    }

    /** The sections with the most to gain, weakest first. Where the improvement advice should aim. */
    public List<SectionReview> weakestSections() {
        return sections.stream()
                .sorted((left, right) -> Integer.compare(left.score(), right.score()))
                .toList();
    }
}

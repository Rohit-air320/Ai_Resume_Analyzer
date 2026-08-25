package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Everything the backend knows about one resume, read from its text alone.
 *
 * <p>The counterpart to a posting's insight, and the reason the two exist in the same shape: an
 * analysis is a comparison, and a comparison between two documents read by two different sets of
 * rules is not one. Nothing here comes from a model.
 *
 * @param skills        catalogue skills the resume claims, most-mentioned first
 * @param sectionsFound which sections the document actually has. Missing sections are as
 *                      interesting as present ones — no SKILLS heading is a real finding.
 * @param years         years of experience, when the document supports a number. Empty means the
 *                      resume gave nothing to work from, which is not the same as zero.
 * @param shape         the measurable shape of the document: length, bullets, contact details,
 *                      layout damage
 * @param text          the normalised text, kept so the prompt can quote from the same string the
 *                      findings were computed from. Never logged, never returned by the API.
 */
public record ResumeInsight(
        List<ResumeSkill> skills,
        Set<ResumeSection> sectionsFound,
        Optional<Integer> years,
        ResumeShape shape,
        String text
) {

    /** An empty resume. Returned rather than throwing, so a caller never has to null-check. */
    public static ResumeInsight empty() {
        return new ResumeInsight(List.of(), Set.of(), Optional.empty(), ResumeShape.empty(), "");
    }

    /** True when there was nothing to read. */
    public boolean isEmpty() {
        return text.isBlank();
    }

    /** Catalogue slugs the resume claims, for set arithmetic against a posting's demands. */
    public Set<String> skillSlugs() {
        return skills.stream().map(ResumeSkill::slug).collect(Collectors.toSet());
    }

    /** The named skill, if the resume claims it. */
    public Optional<ResumeSkill> find(String slug) {
        return skills.stream().filter(skill -> skill.slug().equals(slug)).findFirst();
    }

    /** True when the document has a heading for this section. */
    public boolean has(ResumeSection section) {
        return sectionsFound.contains(section);
    }
}

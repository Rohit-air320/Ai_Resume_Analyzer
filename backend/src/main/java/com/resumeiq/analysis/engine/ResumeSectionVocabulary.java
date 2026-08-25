package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Recognises a resume's own headings, so each section can be assessed separately.
 *
 * <p>Resume headings are far more predictable than job-posting headings: there are perhaps a dozen
 * conventional names for each section and people rarely invent new ones, because a resume with
 * unusual headings is a resume an ATS cannot parse. That predictability is what makes matching on a
 * fixed vocabulary reasonable here, where a posting needed a looser {@code contains}-style match.
 *
 * <h2>Longest keyword wins</h2>
 *
 * <p>"Technical Skills" must resolve to {@code SKILLS} rather than to whichever entry happens to be
 * checked first, and "Work Experience" to {@code EXPERIENCE}. Candidates are therefore ranked by
 * keyword length, which also settles the genuinely ambiguous pair: "Relevant Coursework" contains
 * both "course" and "work", and the longer match is the right one.
 *
 * <p>{@link ResumeSection#FORMATTING} deliberately has no keywords. It is not a section anyone
 * writes — it is a judgement about the document as a whole, which is assessed from the shape of the
 * text rather than found under a heading.
 */
public final class ResumeSectionVocabulary {

    /**
     * Heading words per section, longest-first at match time.
     *
     * <p>All lower case; a heading is folded before comparison. Note the deliberate omissions:
     * "about" is not a summary keyword ("About the company" appears in pasted job text that people
     * sometimes leave in a resume file), and "reference" is not a section here because a
     * "References available on request" line is one of the things the advice tells people to
     * remove.
     */
    private static final Map<ResumeSection, List<String>> KEYWORDS = Map.of(
            ResumeSection.CONTACT, List.of(
                    "contact", "contact information", "contact details", "personal details",
                    "personal information"),
            ResumeSection.SUMMARY, List.of(
                    "summary", "professional summary", "career summary", "profile",
                    "professional profile", "objective", "career objective", "overview"),
            ResumeSection.SKILLS, List.of(
                    "skills", "technical skills", "core skills", "key skills", "core competencies",
                    "competencies", "technologies", "technical proficiencies", "tech stack",
                    "areas of expertise", "expertise"),
            ResumeSection.EXPERIENCE, List.of(
                    "experience", "work experience", "professional experience", "employment",
                    "employment history", "work history", "career history", "internship",
                    "internships", "industry experience"),
            ResumeSection.PROJECTS, List.of(
                    "projects", "personal projects", "academic projects", "key projects",
                    "selected projects", "portfolio", "open source"),
            ResumeSection.EDUCATION, List.of(
                    "education", "academic background", "academics", "qualifications",
                    "educational qualifications", "relevant coursework", "coursework"),
            ResumeSection.CERTIFICATIONS, List.of(
                    "certification", "certifications", "certificates", "licenses", "licences",
                    "courses", "training", "professional development"));

    /** Longest first, so a two-word heading is never resolved by one of its words. */
    private static final List<Candidate> CANDIDATES = KEYWORDS.entrySet().stream()
            .flatMap(entry -> entry.getValue().stream()
                    .map(keyword -> new Candidate(entry.getKey(), keyword)))
            .sorted((left, right) -> Integer.compare(right.keyword().length(),
                    left.keyword().length()))
            .toList();

    private ResumeSectionVocabulary() {
    }

    /**
     * The section a heading names, if any.
     *
     * @param heading one line of a resume, already known to look like a heading
     * @return the section, or empty for a line this vocabulary does not recognise
     */
    public static Optional<ResumeSection> classify(String heading) {
        if (heading == null || heading.isBlank()) {
            return Optional.empty();
        }
        String folded = heading.toLowerCase(Locale.ROOT);
        for (Candidate candidate : CANDIDATES) {
            if (folded.contains(candidate.keyword())) {
                return Optional.of(candidate.section());
            }
        }
        return Optional.empty();
    }

    /** How many heading spellings the vocabulary knows. Exposed so a test can assert it is not empty. */
    public static int size() {
        return CANDIDATES.size();
    }

    private record Candidate(ResumeSection section, String keyword) {
    }
}

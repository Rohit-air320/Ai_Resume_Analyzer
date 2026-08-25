package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.SkillImportance;
import com.resumeiq.analysis.SkillStatus;
import com.resumeiq.common.text.PlainText;
import com.resumeiq.jobdescription.parse.DetectedSkill;
import com.resumeiq.jobdescription.parse.PostingInsight;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Compares what a posting asks for against what a resume shows, skill by skill.
 *
 * <p>Pure, static and the single place the two documents meet. Everything the product says about
 * skills — the match score, the gap list, the recommended projects, the learning plan — is derived
 * from the list this produces, so the rules are written out here rather than spread across the
 * things that consume them.
 *
 * <h2>The two importance scales</h2>
 *
 * <p>A posting's parser speaks in {@code REQUIRED}/{@code PREFERRED}/{@code MENTIONED}, which are
 * facts about where a skill appeared in the text. An analysis speaks in
 * {@code CRITICAL}/{@code IMPORTANT}/{@code NICE_TO_HAVE}, which are judgements about what to do
 * about it. Keeping them as separate enums and translating here is deliberate: the first is what the
 * posting said, the second is advice, and collapsing them would make the parser responsible for
 * advice it has no basis to give.
 *
 * <h2>Strong, partial, missing</h2>
 *
 * <p>A skill is {@code STRONG} when the resume demonstrates it — it appears in experience, projects
 * or certifications, where work is described. It is {@code PARTIAL} when the resume only asserts it,
 * which in practice means it sits in the skills list and nowhere else. It is {@code MISSING} when it
 * is not there at all. That middle case is the most useful thing this comparison produces, because
 * it is actionable in a way the other two are not: the fix is not "learn Docker", it is "say where
 * you used Docker".
 */
public final class SkillComparison {

    private SkillComparison() {
    }

    /**
     * Judges every skill the posting named, then lists the extras the resume brought.
     *
     * @param posting what the job asks for
     * @param resume  what the resume shows
     * @return verdicts for the posting's demands, most important first, then the resume's own
     *         skills that the posting never mentioned
     */
    public static Comparison compare(PostingInsight posting, ResumeInsight resume) {
        List<SkillVerdict> demanded = new ArrayList<>(posting.skills().size());
        for (DetectedSkill wanted : posting.skills()) {
            demanded.add(judge(wanted, resume.find(wanted.slug())));
        }
        demanded.sort(Comparator
                .comparingInt((SkillVerdict verdict) -> -verdict.weight())
                .thenComparingInt(verdict -> verdict.status() == SkillStatus.MISSING ? 0 : 1)
                .thenComparing(SkillVerdict::displayName));

        List<SkillVerdict> extra = new ArrayList<>();
        for (ResumeSkill claimed : resume.skills()) {
            if (posting.skillSlugs().contains(claimed.slug())) {
                continue;
            }
            extra.add(new SkillVerdict(claimed.slug(), claimed.displayName(), claimed.category(),
                    SkillImportance.NICE_TO_HAVE,
                    claimed.isEvidenced() ? SkillStatus.STRONG : SkillStatus.PARTIAL,
                    claimed.mentions(),
                    evidenceFor(claimed), null));
        }
        return new Comparison(List.copyOf(demanded), List.copyOf(extra));
    }

    /**
     * One demand, against what the resume had to say about it.
     *
     * <p>The evidence string is assembled here rather than in the UI, because it has to name the
     * posting's own heading and the resume's own sections, and both of those are known only at this
     * point.
     */
    private static SkillVerdict judge(DetectedSkill wanted, Optional<ResumeSkill> claimed) {
        SkillImportance importance = translate(wanted);
        if (claimed.isEmpty()) {
            String evidence = wanted.foundUnder() == null
                    ? "Not found in your resume. The posting mentions it " + mentions(wanted) + "."
                    : "Not found in your resume. The posting asks for it under \""
                            + wanted.foundUnder() + "\".";
            return new SkillVerdict(wanted.slug(), wanted.displayName(), wanted.category(),
                    importance, SkillStatus.MISSING, 0,
                    PlainText.truncate(evidence, SkillVerdict.MAX_EVIDENCE), wanted.foundUnder());
        }
        ResumeSkill found = claimed.get();
        SkillStatus status = found.isEvidenced() ? SkillStatus.STRONG : SkillStatus.PARTIAL;
        return new SkillVerdict(wanted.slug(), wanted.displayName(), wanted.category(),
                importance, status, found.mentions(),
                PlainText.truncate(evidenceFor(found), SkillVerdict.MAX_EVIDENCE),
                wanted.foundUnder());
    }

    /**
     * Says where in the resume a skill was found, in the user's own structure.
     *
     * <p>Naming the sections is what turns a status into something checkable, and it is also how the
     * partial case explains itself without sounding like an accusation.
     */
    private static String evidenceFor(ResumeSkill found) {
        String where = found.sections().stream()
                .map(section -> section.name().toLowerCase(Locale.ROOT))
                .sorted()
                .reduce((left, right) -> left + ", " + right)
                .orElse("your resume");
        if (found.isEvidenced()) {
            return "Found in " + where + " (" + mentionCount(found.mentions()) + ").";
        }
        return "Listed in " + where + " but not shown in any role or project. "
                + "Naming where you used it is worth more than listing it.";
    }

    /** "3 times" reads better than "3 mentions" in a sentence about a document. */
    private static String mentionCount(int mentions) {
        return mentions == 1 ? "mentioned once" : "mentioned " + mentions + " times";
    }

    private static String mentions(DetectedSkill wanted) {
        return wanted.mentions() == 1 ? "once" : wanted.mentions() + " times";
    }

    /**
     * Translates the posting's reading of a skill into the analysis's advice about it.
     *
     * <p>{@code REQUIRED} becomes {@code CRITICAL}, {@code PREFERRED} becomes {@code IMPORTANT}, and
     * a skill merely mentioned in passing becomes {@code NICE_TO_HAVE}. The last one is the reason
     * this translation is not the identity: a technology named once under "About us" should never be
     * presented as something to go and learn.
     *
     * <p>It takes the whole {@link DetectedSkill} rather than the importance value because the two
     * enums share a simple name. Naming both in one file would mean writing one of them out in full
     * every time it appeared, and a fully-qualified type in a signature is a thing readers skim past
     * — switching over the accessor keeps the mapping legible.
     */
    private static SkillImportance translate(DetectedSkill wanted) {
        return switch (wanted.importance()) {
            case REQUIRED -> SkillImportance.CRITICAL;
            case PREFERRED -> SkillImportance.IMPORTANT;
            case MENTIONED -> SkillImportance.NICE_TO_HAVE;
        };
    }

    /**
     * The finished comparison.
     *
     * @param demanded verdicts for skills the posting asked for, most important first
     * @param extra    catalogue skills the resume claims that this posting never mentioned. Not a
     *                 problem and not padding: these are what the "detected skills" view shows, and
     *                 they are the evidence for suggesting a different kind of role.
     */
    public record Comparison(List<SkillVerdict> demanded, List<SkillVerdict> extra) {

        /** Everything the posting asked for and the resume did not answer. */
        public List<SkillVerdict> gaps() {
            return demanded.stream().filter(SkillVerdict::isGap).toList();
        }

        /** Gaps the posting stated as requirements — the ones worth acting on first. */
        public List<SkillVerdict> criticalGaps() {
            return gaps().stream()
                    .filter(verdict -> verdict.importance() == SkillImportance.CRITICAL)
                    .toList();
        }

        /** Demands the resume answers with demonstrated work. */
        public List<SkillVerdict> strong() {
            return demanded.stream()
                    .filter(verdict -> verdict.status() == SkillStatus.STRONG)
                    .toList();
        }

        /** Demands the resume only asserts. */
        public List<SkillVerdict> partial() {
            return demanded.stream()
                    .filter(verdict -> verdict.status() == SkillStatus.PARTIAL)
                    .toList();
        }

        /** Everything judged, demands first. What Phase 7 persists as {@code analysis_skills}. */
        public List<SkillVerdict> all() {
            List<SkillVerdict> everything = new ArrayList<>(demanded);
            everything.addAll(extra);
            return List.copyOf(everything);
        }

        /** True when the posting named no catalogue skill, so there is nothing to compare. */
        public boolean isUnmeasurable() {
            return demanded.isEmpty();
        }
    }
}

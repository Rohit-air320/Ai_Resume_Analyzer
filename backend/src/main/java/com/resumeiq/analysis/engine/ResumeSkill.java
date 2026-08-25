package com.resumeiq.analysis.engine;

import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.skill.SkillCategory;

import java.util.Set;

/**
 * One catalogue skill a resume claims, and the evidence for it.
 *
 * @param slug        catalogue slug, which is what makes this comparable to a posting's demand
 * @param displayName the catalogue's spelling, so "react" in a resume is shown as "React"
 * @param category    the catalogue's category, used to group the skill-gap view
 * @param mentions    how many times it was found across the whole document
 * @param sections    which resume sections it appeared in — the interesting part, because a skill
 *                    named only in the skills list is asserted while one that also appears in a
 *                    project or a role is demonstrated
 */
public record ResumeSkill(
        String slug,
        String displayName,
        SkillCategory category,
        int mentions,
        Set<ResumeSection> sections
) {

    /**
     * True when the skill appears somewhere that describes doing the work.
     *
     * <p>This is the distinction the scoring cares about and the one people most need told. Anyone
     * can add "Kubernetes" to a list; a resume where every skill appears only in the list and never
     * in a project or a role reads as a keyword dump to a human reviewer, however well it scores
     * against a machine. Experience, projects and certifications are the three places a claim gets
     * backed up.
     */
    public boolean isEvidenced() {
        return sections.contains(ResumeSection.EXPERIENCE)
                || sections.contains(ResumeSection.PROJECTS)
                || sections.contains(ResumeSection.CERTIFICATIONS);
    }

    /** True when the skill was found in the skills list, wherever else it appears. */
    public boolean isListed() {
        return sections.contains(ResumeSection.SKILLS);
    }
}

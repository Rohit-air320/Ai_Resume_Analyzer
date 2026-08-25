package com.resumeiq.jobdescription;

import com.resumeiq.jobdescription.parse.DetectedSkill;
import com.resumeiq.jobdescription.parse.ExperienceDemand;
import com.resumeiq.jobdescription.parse.Keyword;
import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.jobdescription.parse.PostingSection;
import com.resumeiq.jobdescription.parse.SkillImportance;
import com.resumeiq.skill.SkillCategory;
import com.resumeiq.user.ExperienceLevel;

import java.util.List;

/**
 * What the backend read out of a posting, as a client sees it.
 *
 * <p>The skills arrive in three lists rather than one list carrying an importance field. That is a
 * choice about the UI: "Required" and "Nice to have" are different headings on the page, and a
 * frontend that has to group an array by an enum before it can render it will grow a helper for
 * doing so in every component that touches this data. Three lists is the same information, already
 * in the shape the screen needs.
 *
 * <p>Nothing here is stored, so nothing here is a schema. It is recomputed from the posting text on
 * every read — see {@link com.resumeiq.jobdescription.parse.JobPostingParser} for why that is the
 * point rather than an inefficiency.
 *
 * @param requiredSkills  what the posting says it needs
 * @param preferredSkills nice-to-haves, which is where the cheapest wins usually are
 * @param mentionedSkills technologies that appear somewhere without being asked for. Kept separate
 *                        and last, because a skill named once in passing is not a requirement and
 *                        presenting it as one sends people off to learn things nobody asked for.
 * @param keywords        terms the posting leans on that are not catalogue skills
 * @param experience      how much experience it wants, with the words that said so
 * @param wordCount       length of the posting
 * @param structured      whether the posting had headings this parser recognised. When false the
 *                        whole posting was read as requirements, so the importance labels are a
 *                        reasonable default rather than something the text actually said — and the
 *                        UI can say so instead of overstating what it knows.
 * @param sectionsFound   the kinds of section the posting's own headings named. Enum names, not
 *                        prose, so display copy stays in the frontend where it can be translated
 */
public record PostingInsightResponse(
        List<SkillDemand> requiredSkills,
        List<SkillDemand> preferredSkills,
        List<SkillDemand> mentionedSkills,
        List<KeywordDemand> keywords,
        Experience experience,
        int wordCount,
        boolean structured,
        List<String> sectionsFound
) {

    public static PostingInsightResponse from(PostingInsight insight) {
        return new PostingInsightResponse(
                skillsOf(insight, SkillImportance.REQUIRED),
                skillsOf(insight, SkillImportance.PREFERRED),
                skillsOf(insight, SkillImportance.MENTIONED),
                insight.keywords().stream().map(KeywordDemand::from).toList(),
                Experience.from(insight),
                insight.wordCount(),
                insight.structured(),
                insight.sectionsFound().stream().map(PostingSection::name).toList());
    }

    private static List<SkillDemand> skillsOf(PostingInsight insight, SkillImportance importance) {
        return insight.skills().stream()
                .filter(skill -> skill.importance() == importance)
                .map(SkillDemand::from)
                .toList();
    }

    /**
     * One skill the posting asks for.
     *
     * @param slug        canonical key, the thing the frontend compares against a resume's skills
     * @param name        the catalogue's spelling, which is what gets displayed
     * @param category    for grouping the list
     * @param mentions    how many times it appears. Evidence, not a score
     * @param foundUnder  the heading it was found under, as the poster wrote it, or absent when
     *                    there was no heading. Shown next to the skill so the reader can check the
     *                    claim against the posting instead of taking our word for it
     */
    public record SkillDemand(String slug, String name, SkillCategory category, int mentions,
                              String foundUnder) {

        static SkillDemand from(DetectedSkill skill) {
            return new SkillDemand(skill.slug(), skill.displayName(), skill.category(),
                    skill.mentions(), skill.foundUnder());
        }
    }

    /**
     * One term worth using the posting's own words for.
     *
     * <p>{@code score} is deliberately not exposed. It is a section-weighted count that only means
     * anything relative to the other keywords of the same posting, and a number on screen invites
     * being read as a percentage. The ordering it produced is the part that is useful, and the array
     * order carries that.
     *
     * @param term           the term, spelled the way the posting spelled it
     * @param occurrences    how many times it appears in a section that asks for something
     * @param inRequirements whether it came from the requirements or the day-to-day work, which is
     *                       what separates "the job is about this" from "this was mentioned"
     */
    public record KeywordDemand(String term, int occurrences, boolean inRequirements) {

        static KeywordDemand from(Keyword keyword) {
            return new KeywordDemand(keyword.term(), keyword.occurrences(),
                    keyword.isFromDemandingSection());
        }
    }

    /**
     * The experience the posting asks for.
     *
     * <p>Every component is nullable and the whole object is null when the posting never said,
     * which is a different thing from saying no experience is needed. The frontend has to be able to
     * tell those apart: "no experience requirement stated" is useful, and "0 years required" would
     * be a claim the posting never made.
     *
     * @param minYears fewest years asked for
     * @param maxYears top of a stated range, absent when it is open-ended
     * @param level    the band, comparable with the level on the user's own profile
     * @param evidence the words this was read from, so the reader can check it
     */
    public record Experience(Integer minYears, Integer maxYears, ExperienceLevel level,
                             String evidence) {

        static Experience from(PostingInsight insight) {
            ExperienceDemand demand = insight.experience();
            if (!demand.isStated()) {
                return null;
            }
            return new Experience(demand.minYears(), demand.maxYears(), demand.level(),
                    demand.evidence());
        }
    }
}

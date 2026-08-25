package com.resumeiq.jobdescription.parse;

import com.resumeiq.user.ExperienceLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reading how much experience a posting asks for.
 *
 * <p>Two behaviours here are choices rather than parsing, and they are what most of these tests are
 * about. Where a posting states several numbers, the smallest one from a section that is asking for
 * something wins — being told a job needs three years when it says five costs a person nothing,
 * being told it needs five when it says three costs them an application. And the evidence string
 * has to be whichever reading actually decided the band, because a claim about seniority that
 * quotes the wrong words looks like a bug to the only person who can check it.
 */
class ExperienceDemandTest {

    @Test
    @DisplayName("a stated minimum is read with the phrase around it")
    void readsAStatedMinimum() {
        ExperienceDemand demand = detect("Backend Engineer",
                requirements("Minimum 3 years of experience building web services"));

        assertThat(demand.minYears()).isEqualTo(3);
        assertThat(demand.maxYears()).isNull();
        assertThat(demand.level()).isEqualTo(ExperienceLevel.MID);
        // "3 years" would be true and would read like a machine. The posting said "Minimum 3 years".
        assertThat(demand.evidence()).isEqualTo("Minimum 3 years");
        assertThat(demand.isStated()).isTrue();
    }

    @Test
    @DisplayName("a range keeps both ends, and an open-ended number keeps only one")
    void readsARange() {
        assertThat(detect("Engineer", requirements("3-5 years of experience")))
                .satisfies(demand -> {
                    assertThat(demand.minYears()).isEqualTo(3);
                    assertThat(demand.maxYears()).isEqualTo(5);
                    assertThat(demand.evidence()).isEqualTo("3-5 years");
                });
        assertThat(detect("Engineer", requirements("3 to 5 yrs in a similar role")))
                .satisfies(demand -> {
                    assertThat(demand.minYears()).isEqualTo(3);
                    assertThat(demand.maxYears()).isEqualTo(5);
                });
        assertThat(detect("Engineer", requirements("at least 7 years of backend work")))
                .satisfies(demand -> {
                    assertThat(demand.minYears()).isEqualTo(7);
                    assertThat(demand.maxYears()).isNull();
                    assertThat(demand.level()).isEqualTo(ExperienceLevel.SENIOR);
                    assertThat(demand.evidence()).isEqualTo("at least 7 years");
                });
    }

    @Test
    @DisplayName("years fall into bands, and two years is junior rather than entry")
    void bandsTheYears() {
        assertThat(detect("Engineer", requirements("2 years of experience")).level())
                .isEqualTo(ExperienceLevel.JUNIOR);
        assertThat(detect("Engineer", requirements("5+ years of experience")).level())
                .isEqualTo(ExperienceLevel.MID);
        assertThat(detect("Engineer", requirements("6+ years of experience")).level())
                .isEqualTo(ExperienceLevel.SENIOR);
    }

    @Test
    @DisplayName("no number of years ever reads as lead, because leading is not years")
    void neverReadsLeadFromYears() {
        ExperienceDemand demand = detect("Backend Engineer", requirements("12 years of experience"));

        // Twelve years of writing code is not managing people, and a posting for an individual
        // contributor should not be described to a user as a leadership role.
        assertThat(demand.level()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(demand.minYears()).isEqualTo(12);
    }

    @Test
    @DisplayName("the smallest number in a section is the bar to clear")
    void takesTheSmallestNumber() {
        ExperienceDemand demand = detect("Engineer",
                requirements("5+ years of Java, or 3+ years with a computer science degree"));

        assertThat(demand.minYears()).isEqualTo(3);
        assertThat(demand.evidence()).isEqualTo("3+ years");
    }

    @Test
    @DisplayName("a number in a demanding section beats a smaller one in the nice-to-haves")
    void prefersTheSectionThatIsAsking() {
        ExperienceDemand demand = detect("Engineer",
                requirements("5+ years of backend experience"),
                new PostingBlock(PostingSection.PREFERRED, "Nice to have",
                        "2 years working with Kubernetes"));

        // The two years are about Kubernetes specifically. The hiring bar is the five.
        assertThat(demand.minYears()).isEqualTo(5);
        assertThat(demand.evidence()).isEqualTo("5+ years");
    }

    @Test
    @DisplayName("the company's own history is not a requirement")
    void ignoresTheBenefitsAndBoilerplate() {
        ExperienceDemand demand = detect("Backend Engineer",
                new PostingBlock(PostingSection.COMPANY, "About us",
                        "Our founders bring 20 years of combined industry experience"));

        assertThat(demand.isStated()).isFalse();
        assertThat(demand).isEqualTo(ExperienceDemand.unknown());
        // Not the same as "no experience needed", and nothing downstream may say that it is.
        assertThat(demand.minYears()).isNull();
        assertThat(demand.level()).isNull();
        assertThat(demand.evidence()).isNull();
    }

    @Test
    @DisplayName("an implausible or empty number is not a career length")
    void filtersNumbersThatCannotBeYears() {
        assertThat(detect("Engineer", requirements("Founded 50 years ago, 0 years required"))
                .isStated()).isFalse();
        // A backwards range is a typo in the posting, so the minimum stands and the top is dropped
        // rather than reported as a maximum below it.
        assertThat(detect("Engineer", requirements("5-3 years of experience")))
                .satisfies(demand -> {
                    assertThat(demand.minYears()).isEqualTo(5);
                    assertThat(demand.maxYears()).isNull();
                });
    }

    @Test
    @DisplayName("the title is read when the body never says a number")
    void readsTheTitle() {
        ExperienceDemand demand = detect("Senior Backend Engineer",
                requirements("Strong Java and Spring Boot skills"));

        assertThat(demand.level()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(demand.minYears()).isNull();
        assertThat(demand.evidence()).isEqualTo("senior");
    }

    @Test
    @DisplayName("entry and lead can only come from a title")
    void readsBandsOnlyATitleCanGive() {
        assertThat(detect("Graduate Software Engineer", requirements("Curiosity")).level())
                .isEqualTo(ExperienceLevel.ENTRY);
        assertThat(detect("Engineering Manager", requirements("Curiosity")).level())
                .isEqualTo(ExperienceLevel.LEAD);
        // Strongest word first, so this is a staff role that happens to say senior.
        assertThat(detect("Senior Staff Engineer", requirements("Curiosity")))
                .satisfies(demand -> {
                    assertThat(demand.level()).isEqualTo(ExperienceLevel.LEAD);
                    assertThat(demand.evidence()).isEqualTo("staff");
                });
    }

    @Test
    @DisplayName("the more senior of the two readings wins, and the evidence follows it")
    void quotesWhicheverReadingDecided() {
        ExperienceDemand titleWins = detect("Senior Backend Engineer",
                requirements("5+ years of experience"));

        // Five years is MID, the title says SENIOR, so the answer is SENIOR — and quoting
        // "5+ years" beside a SENIOR badge would read as a contradiction.
        assertThat(titleWins.level()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(titleWins.evidence()).isEqualTo("senior");
        // The number is still reported. It is a fact about the posting either way.
        assertThat(titleWins.minYears()).isEqualTo(5);

        ExperienceDemand yearsWin = detect("Senior Backend Engineer",
                requirements("6+ years of experience"));

        // Both readings agree, and years win the tie: a number is a fact where a title is a
        // convention that varies by company.
        assertThat(yearsWin.level()).isEqualTo(ExperienceLevel.SENIOR);
        assertThat(yearsWin.evidence()).isEqualTo("6+ years");
    }

    @Test
    @DisplayName("a posting that says nothing about experience says so")
    void handlesASilentPosting() {
        assertThat(detect("Backend Engineer", requirements("Java, Spring Boot, MySQL")))
                .isEqualTo(ExperienceDemand.unknown());
        assertThat(detect(null).isStated()).isFalse();
        assertThat(detect("   ", requirements("   ")).isStated()).isFalse();
    }

    // ---------------------------------------------------------------- helpers

    private static ExperienceDemand detect(String title, PostingBlock... blocks) {
        return ExperienceDemand.detect(List.of(blocks), title);
    }

    private static PostingBlock requirements(String text) {
        return new PostingBlock(PostingSection.REQUIREMENTS, "Requirements", text);
    }
}

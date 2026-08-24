package com.resumeiq.analysis;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One keyword the analysis has an opinion about.
 *
 * <p>Keywords are stored as values rather than resolved against the skill taxonomy on purpose.
 * A skill is a capability the gap analysis reasons about over time; a keyword is a phrase from
 * one specific posting — "cross-functional", "SLA", "cost optimisation" — that matters for that
 * application and nowhere else. Forcing both through the same table would fill the taxonomy
 * with thousands of one-off phrases and make the skill-gap counts meaningless.
 *
 * <p>{@code placement} is what keeps this feature honest. The spec forbids encouraging keyword
 * stuffing, so a suggested keyword travels with the place it would legitimately belong — "in
 * the Payments project bullet, where you already describe the retry logic" — rather than as a
 * bare term to sprinkle in.
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AnalysisKeyword {

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 20)
    private KeywordKind kind;

    @Column(name = "term", nullable = false, length = 120)
    private String term;

    /** Where this term would truthfully fit. Null for matched keywords, which need no advice. */
    @Column(name = "placement", length = 300)
    private String placement;
}

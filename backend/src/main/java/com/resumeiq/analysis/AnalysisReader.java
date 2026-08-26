package com.resumeiq.analysis;

import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.security.AuthenticatedUser;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Reads analyses back out.
 *
 * <h2>Why the reads are a bean of their own</h2>
 *
 * <p>Because {@link AnalysisService#create} needs one. A create response is this same document, read
 * back from the database, and a {@code @Transactional} method calling another {@code @Transactional}
 * method on its own class goes straight past Spring's proxy — the annotation is there in the source and
 * absent at run time. That is not a theoretical hazard here: {@link #read} maps three
 * {@code @ElementCollection}s, which are lazy, so a self-invoked call would have loaded the analysis in
 * one transaction and then thrown {@code LazyInitializationException} on the first keyword. So the
 * boundary is a bean boundary, the same way {@link AnalysisWriter} is.
 *
 * <p>The result is a layout where every transaction in this feature belongs to exactly one class:
 * writes to {@code AnalysisWriter}, reads to here, the delete to the repository method that declares
 * it, and {@code AnalysisService} — the class that calls a language model — to none of them.
 */
@Component
public class AnalysisReader {

    /**
     * How many analyses a history request returns.
     *
     * <p>A cap rather than a page parameter. The history screen is a list you scan, nobody has run
     * three hundred analyses yet, and an unbounded query against a table that only grows is the kind
     * of thing that works for a year and then does not. Paging is a Phase 8 concern if the screen
     * turns out to want it.
     */
    private static final int HISTORY_LIMIT = 100;

    private final AnalysisRepository analyses;

    public AnalysisReader(AnalysisRepository analyses) {
        this.analyses = analyses;
    }

    /**
     * The caller's analyses, newest first.
     *
     * <p>Reads the summary projection, so a hundred rows do not drag a hundred {@code LONGTEXT}
     * feedback columns and four hundred child collections into memory to render a table of scores.
     */
    @Transactional(readOnly = true)
    public List<AnalysisSummaryResponse> list(AuthenticatedUser caller) {
        return analyses.findSummariesForUser(caller.id(), PageRequest.of(0, HISTORY_LIMIT)).stream()
                .map(AnalysisSummaryResponse::from)
                .toList();
    }

    /**
     * One analysis in full.
     *
     * <p>Uses the entity graph that fetches the skills, their catalogue rows and the recommendations
     * in one query. Without it, a response that reads {@code skill.getSlug()} on twenty skills is
     * twenty extra selects — the N+1 that stays invisible until real analyses have real skill lists.
     *
     * <p>Ownership is in the query rather than checked after it, and a miss is a 404 rather than a
     * 403: "that exists, but not for you" is itself a fact about another account.
     */
    @Transactional(readOnly = true)
    public AnalysisResponse read(AuthenticatedUser caller, UUID publicId) {
        Analysis analysis = analyses.findDetailByPublicIdAndUserId(publicId, caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Analysis", publicId));
        return AnalysisResponse.from(analysis);
    }
}

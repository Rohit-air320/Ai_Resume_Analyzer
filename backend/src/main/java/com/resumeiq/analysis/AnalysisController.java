package com.resumeiq.analysis;

import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Running and reading analyses.
 *
 * <p>Four endpoints over a resume and a posting the caller has already saved. The request body is two
 * identifiers and nothing else — no resume text, no job text, no scores, no model name. Anything a
 * client could put in those fields would be a claim about a document rather than the document, and the
 * only trustworthy version of it is already in the database under a row this user owns.
 *
 * <p>Every method takes {@code caller} first. That is the project's authorisation habit: the check is a
 * parameter the method cannot be invoked without, rather than a line at the top of the body that a
 * future endpoint might not copy.
 */
@RestController
@RequestMapping("/api/analyses")
@Tag(name = "Analyses", description = "Score a resume against a job description and read the results")
public class AnalysisController {

    private final AnalysisService analyses;

    public AnalysisController(AnalysisService analyses) {
        this.analyses = analyses;
    }

    /**
     * Analyses a resume against a posting.
     *
     * <p>201, and the body is the finished analysis rather than a job id. The request runs the whole
     * thing — extraction is already done, the scoring is arithmetic, and the only slow part is the
     * provider call, which falls back to a computed writer rather than hanging. A client that wants a
     * processing screen gets one for free: the screen is what it shows while this request is in flight.
     *
     * <p>The status code is worth being deliberate about. This is not idempotent and is not meant to be
     * — running the same resume against the same posting twice is how a user checks whether an edit
     * helped, so each call creates a row and the history is the point.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Analyse a resume against a job description",
            description = "Scores the resume, stores the result and returns it in full: the six "
                    + "scores with the reasons behind each one, detected and missing skills, matched "
                    + "and suggested keywords, per-section assessments, and the improvements, "
                    + "projects and learning topics that follow from them. Responds 404 if either "
                    + "document is missing or belongs to another account, and 422 if the resume "
                    + "yielded no readable text — an empty resume scores low for the wrong reason, so "
                    + "it is refused rather than scored. Repeat calls are allowed and create separate "
                    + "analyses; that is how the history chart gets its points.")
    public AnalysisResponse create(@CurrentUser AuthenticatedUser caller,
                                   @Valid @RequestBody CreateAnalysisRequest request) {
        return analyses.create(caller, request);
    }

    @GetMapping
    @Operation(
            summary = "List your analyses",
            description = "Newest first, capped at the most recent hundred. Scores and labels only — "
                    + "the skills, keywords and advice come with a single analysis, not with a list of "
                    + "them.")
    public List<AnalysisSummaryResponse> list(@CurrentUser AuthenticatedUser caller) {
        return analyses.list(caller);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one analysis",
            description = "The same document that creating it returned, read from storage. Scores and "
                    + "advice are as they were computed, not recalculated — an analysis from three "
                    + "months ago is still explained by the rules that produced its numbers. Responds "
                    + "404 if it does not exist or is not yours.")
    public AnalysisResponse get(@CurrentUser AuthenticatedUser caller, @PathVariable UUID id) {
        return analyses.read(caller, id);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete an analysis",
            description = "Removes the analysis and everything derived from it — skills, keywords, "
                    + "section scores and recommendations. The resume and the job description are left "
                    + "alone, so the same pair can be analysed again. Responds 404 if it does not "
                    + "exist or is not yours.")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser caller,
                                       @PathVariable UUID id) {
        analyses.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}

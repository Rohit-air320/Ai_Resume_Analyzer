package com.resumeiq.recommendation;

import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Advice across every analysis.
 *
 * <p>One endpoint, one optional filter. The type is a query parameter rather than four separate paths
 * because the client renders it as tabs over one list, and four endpoints would be four things to keep
 * in step for a filter the database applies with a {@code where}.
 */
@RestController
@RequestMapping("/api/recommendations")
@Tag(name = "Recommendations", description = "Improvements, projects, learning topics and keywords "
        + "gathered from every analysis")
public class RecommendationController {

    private final RecommendationService recommendations;

    public RecommendationController(RecommendationService recommendations) {
        this.recommendations = recommendations;
    }

    @GetMapping
    @Operation(
            summary = "List your recommendations",
            description = "Every suggestion from every analysis, newest first and capped at a hundred, "
                    + "each carrying the job title it came from so it can be read in context. Pass "
                    + "type to narrow to one kind. Responds 400 for a type that is not one of the four; "
                    + "an account with no analyses gets an empty list.")
    public List<RecommendationResponse> list(
            @CurrentUser AuthenticatedUser caller,
            @Parameter(description = "Narrow to one kind of advice. Omit for all four.")
            @RequestParam(required = false) RecommendationType type) {

        return recommendations.list(caller, type);
    }
}

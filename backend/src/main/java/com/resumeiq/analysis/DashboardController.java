package com.resumeiq.analysis;

import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The dashboard, in one request.
 *
 * <p>Deliberately not a resource. There is no dashboard row, nothing to create and nothing to delete —
 * this is a read of five aggregates that happen to share a screen, and pretending otherwise would buy
 * REST purity at the cost of five round trips and five loading states.
 */
@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard", description = "Counts, trend and highlights for the signed-in account")
public class DashboardController {

    private final DashboardService dashboard;

    public DashboardController(DashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping
    @Operation(
            summary = "Get your dashboard",
            description = "Counts of analyses, resumes and job descriptions; average, best and latest "
                    + "overall score; up to thirty points of score history oldest first; the five most "
                    + "recent analyses; and the six skills most often flagged as missing across the "
                    + "account. A new account gets zeroes, nulls and empty lists rather than an error — "
                    + "the average is null and not zero, because nothing scored yet is not a score of "
                    + "nothing.")
    public DashboardResponse get(@CurrentUser AuthenticatedUser caller) {
        return dashboard.of(caller);
    }
}

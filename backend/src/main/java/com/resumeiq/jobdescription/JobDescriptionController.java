package com.resumeiq.jobdescription;

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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Saving and managing target job descriptions.
 *
 * <p>As with resumes, every method takes the caller as its first parameter and nothing here accepts a
 * user id from the request. Authorisation is an argument the method cannot be called without rather
 * than a step somebody might forget.
 *
 * <p>The parse comes back with the posting on create, which is the point of doing it in code rather
 * than in a prompt: the moment somebody pastes a description they can see the skills it asks for and
 * the seniority it wants, with no AI call, no spinner and no key. If the provider were down the
 * screen would still be useful.
 */
@RestController
@RequestMapping("/api/job-descriptions")
@Tag(name = "Job descriptions", description = "Save target job descriptions and read what they ask for")
public class JobDescriptionController {

    private final JobDescriptionService postings;

    public JobDescriptionController(JobDescriptionService postings) {
        this.postings = postings;
    }

    /**
     * Saves a posting and returns what was read out of it.
     *
     * <p>200 rather than 201 for a posting the user has already saved. That is what the status code
     * is for, and it saves the client from having to compare identifiers to work out whether a row
     * appeared.
     */
    @PostMapping
    @Operation(
            summary = "Save a job description",
            description = "Stores the posting and returns the skills, keywords and experience level "
                    + "read out of it. Responds 201 for a new posting and 200 with the existing one "
                    + "if you have pasted this description before — re-pasting is the normal way to "
                    + "re-analyse an updated resume, not an error. Responds 400 if the text is too "
                    + "short to score against, and 409 once the per-account limit is reached. Text "
                    + "beyond the maximum length is truncated rather than refused, because postings "
                    + "run long at the end where the boilerplate is.")
    public ResponseEntity<JobDescriptionResponse> create(
            @CurrentUser AuthenticatedUser caller,
            @Valid @RequestBody CreateJobDescriptionRequest request) {

        JobDescriptionService.Saved saved = postings.create(caller, request);
        HttpStatus status = saved.reused() ? HttpStatus.OK : HttpStatus.CREATED;
        return ResponseEntity.status(status).body(saved.posting());
    }

    @GetMapping
    @Operation(
            summary = "List your job descriptions",
            description = "Newest first. Metadata only — neither the posting text nor its parse is "
                    + "included in a list.")
    public List<JobDescriptionResponse> list(@CurrentUser AuthenticatedUser caller) {
        return postings.list(caller);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one job description",
            description = "Includes the posting text and a fresh parse of it, so a posting saved "
                    + "weeks ago benefits from skills added to the catalogue since. Responds 404 if "
                    + "it does not exist or is not yours.")
    public JobDescriptionResponse get(@CurrentUser AuthenticatedUser caller, @PathVariable UUID id) {
        return postings.get(caller, id);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a job description",
            description = "Responds 404 if it does not exist or is not yours.")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser caller, @PathVariable UUID id) {
        postings.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}

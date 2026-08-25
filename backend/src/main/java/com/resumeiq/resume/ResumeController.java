package com.resumeiq.resume;

import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Resume upload and management.
 *
 * <p>Every method takes the caller as its first parameter. That is the point of the
 * signature: authorisation is not a step somebody might remember to perform, it is an
 * argument the method cannot be called without. Nothing here accepts a user id from the
 * request — an endpoint that did would let anyone read anyone's resume by changing a
 * number, which is the flaw the spec names first.
 *
 * <p>There is deliberately no download endpoint. Nothing in the product needs to hand the
 * original file back: the analysis works from extracted text, and the resume list shows
 * metadata. Adding one would mean serving user-uploaded binaries from the API's own origin,
 * where a crafted "PDF" that a browser decides to render as HTML becomes stored XSS against
 * every signed-in user. The file is written once, read once, and never served.
 */
@RestController
@RequestMapping("/api/resumes")
@Validated
@Tag(name = "Resumes", description = "Upload resumes and manage the ones already uploaded")
public class ResumeController {

    /** Matches the column, and is generous — this is a name in a list, not a document. */
    private static final int MAX_LABEL_LENGTH = 140;

    private final ResumeService resumes;

    public ResumeController(ResumeService resumes) {
        this.resumes = resumes;
    }

    /**
     * Accepts one PDF or DOCX and reads its text.
     *
     * <p>{@code consumes} is multipart only, so a JSON body to this path is refused by the
     * framework as a 415 before any of our code runs.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Upload a resume",
            description = "Accepts a PDF or DOCX, stores it, and extracts its text. The format is "
                    + "determined from the file's own contents, not from its name or the "
                    + "Content-Type sent with it. Responds 415 for any other format, 413 above the "
                    + "size limit, and 409 once the per-account limit is reached. A file that "
                    + "stores but cannot be read is still created, with a status explaining why.")
    public ResponseEntity<ResumeResponse> upload(
            @CurrentUser AuthenticatedUser caller,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "label", required = false)
            @Size(max = MAX_LABEL_LENGTH, message = "Label must be at most 140 characters")
            String label) {

        ResumeResponse created = resumes.upload(caller, file, label);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    @Operation(
            summary = "List your resumes",
            description = "Newest first. Metadata only — resume text is never included in a list.")
    public List<ResumeResponse> list(@CurrentUser AuthenticatedUser caller) {
        return resumes.list(caller);
    }

    @GetMapping("/{id}")
    @Operation(
            summary = "Get one resume",
            description = "Includes a short excerpt of the extracted text, so you can confirm the "
                    + "right document was read. Responds 404 if it does not exist or is not yours.")
    public ResumeResponse get(@CurrentUser AuthenticatedUser caller, @PathVariable UUID id) {
        return resumes.get(caller, id);
    }

    @DeleteMapping("/{id}")
    @Operation(
            summary = "Delete a resume",
            description = "Removes the record and the stored file. Responds 404 if it does not "
                    + "exist or is not yours.")
    public ResponseEntity<Void> delete(@CurrentUser AuthenticatedUser caller, @PathVariable UUID id) {
        resumes.delete(caller, id);
        return ResponseEntity.noContent().build();
    }
}

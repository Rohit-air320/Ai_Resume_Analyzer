package com.resumeiq.jobdescription;

import com.resumeiq.common.exception.BadRequestException;
import com.resumeiq.common.exception.ConflictException;
import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.common.text.PlainText;
import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.jobdescription.parse.JobPostingParser;
import com.resumeiq.jobdescription.parse.PostingInsight;
import com.resumeiq.security.AuthenticatedUser;
import com.resumeiq.user.User;
import com.resumeiq.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Saving, reading and deleting job descriptions.
 *
 * <p>Ownership works the way it does everywhere else in this project: every lookup goes through
 * {@code findByPublicIdAndUserId}, so there is no path through this class that could fetch a posting
 * without asking whose it is, and a miss is a 404 rather than a 403 — "that exists, but not for you"
 * is itself information about another account.
 *
 * <h2>Re-pasting the same posting is not an error</h2>
 *
 * <p>The core loop of this product is one posting and several versions of a resume. People paste the
 * same description again on Tuesday because they have rewritten their bullet points since Monday,
 * and a 409 there would be technically defensible and infuriating. So a create that hashes to a
 * posting the user already has returns that posting with 200 instead of 201, and the analysis history
 * for that job stays on one row where "my scores over time for this job" can be asked of it.
 *
 * <h2>Text is truncated, not refused</h2>
 *
 * <p>Above the configured maximum the posting is cut rather than rejected. This is worth stating
 * plainly because the opposite is the obvious choice: postings are long at the <em>end</em> — perks,
 * the EEO statement, how to apply — so cutting there keeps every requirement and drops the
 * boilerplate. Refusing a 25,000-character posting would mean telling somebody to edit a job
 * description before we would look at it. Below the configured minimum it <em>is</em> refused,
 * because scoring a resume against three lines produces a confident number that means nothing.
 */
@Service
public class JobDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(JobDescriptionService.class);

    /** Matches the columns. Both are truncated rather than refused; the DTO bounds them first. */
    private static final int MAX_TITLE_LENGTH = 160;

    private final JobDescriptionRepository postings;
    private final UserRepository users;
    private final JobPostingParser parser;
    private final ResumeIqProperties.Posting limits;

    public JobDescriptionService(JobDescriptionRepository postings,
                                UserRepository users,
                                JobPostingParser parser,
                                ResumeIqProperties properties) {
        this.postings = postings;
        this.users = users;
        this.parser = parser;
        this.limits = properties.posting();
    }

    /**
     * Saves a posting, or returns the one the user already had.
     *
     * <p>The order of the two guards is the interesting part. Reuse is checked before the quota, so
     * somebody sitting at fifty saved postings can still re-paste one of those fifty and get on with
     * their analysis. Checking the quota first would refuse a request that was never going to create
     * a row.
     *
     * <p>Two identical pastes racing each other end at the unique constraint on
     * {@code (user_id, content_hash)}, which surfaces as a 409. That is the honest outcome: the
     * constraint is the guarantee, and retrying inside this transaction would not work anyway once
     * the integrity violation has marked it rollback-only.
     */
    @Transactional
    public Saved create(AuthenticatedUser caller, CreateJobDescriptionRequest request) {
        String text = textOf(request.text());
        String hash = JobDescription.hashOf(text);

        Optional<JobDescription> existing =
                postings.findByUserIdAndContentHash(caller.id(), hash);
        if (existing.isPresent()) {
            JobDescription posting = existing.get();
            log.debug("Reusing job description {} for user {}",
                    posting.getPublicId(), caller.publicId());
            return new Saved(response(posting), true);
        }

        enforceQuota(caller);
        User owner = users.getReferenceById(caller.id());
        JobDescription saved = postings.save(JobDescription.builder()
                .user(owner)
                .title(truncate(request.title().strip(), MAX_TITLE_LENGTH))
                .company(companyOf(request.company()))
                .rawText(text)
                .contentHash(hash)
                .build());

        // Counts and identifiers only. The posting itself is the user's data and never reaches a
        // log line, for the same reason resume text does not.
        log.info("Saved job description {} for user {} ({} characters)",
                saved.getPublicId(), caller.publicId(), text.length());
        return new Saved(response(saved), false);
    }

    /**
     * Every posting this user owns, newest first.
     *
     * <p>Uses the summary projection, so the {@code LONGTEXT} column is not in the query and the
     * parser is not run fifty times to render a list that shows none of its output.
     */
    @Transactional(readOnly = true)
    public List<JobDescriptionResponse> list(AuthenticatedUser caller) {
        return postings.findSummariesByUserIdOrderByCreatedAtDesc(caller.id()).stream()
                .map(JobDescriptionResponse::from)
                .toList();
    }

    /** One posting, with the text and a fresh parse of it. */
    @Transactional(readOnly = true)
    public JobDescriptionResponse get(AuthenticatedUser caller, UUID publicId) {
        return response(require(caller, publicId));
    }

    /**
     * Deletes a posting.
     *
     * <p>Nothing on disk to clean up, which makes this simpler than deleting a resume — but note
     * that the owner is in the delete statement as well as in the lookup above it. Belt and braces
     * on purpose: this is the layer where a mistake means deleting somebody else's row.
     */
    @Transactional
    public void delete(AuthenticatedUser caller, UUID publicId) {
        int removed = postings.deleteByPublicIdAndUserId(publicId, caller.id());
        if (removed != 1) {
            throw new ResourceNotFoundException("Job description", publicId);
        }
        log.info("Deleted job description {} for user {}", publicId, caller.publicId());
    }

    /** The ownership-scoped lookup every read in this class goes through. */
    private JobDescription require(AuthenticatedUser caller, UUID publicId) {
        return postings.findByPublicIdAndUserId(publicId, caller.id())
                .orElseThrow(() -> new ResourceNotFoundException("Job description", publicId));
    }

    private JobDescriptionResponse response(JobDescription posting) {
        PostingInsight insight = parser.parse(posting.getRawText(), posting.getTitle());
        return JobDescriptionResponse.from(posting, PostingInsightResponse.from(insight));
    }

    /**
     * Normalises the pasted text, enforces the floor, and applies the ceiling.
     *
     * <p>Normalisation happens before the length check rather than after, which matters more than it
     * looks: a paste out of a PDF viewer arrives padded with runs of spaces and soft hyphens, and
     * measuring that against the minimum would pass text that has almost no words in it.
     */
    private String textOf(String rawText) {
        String text = PlainText.normalise(rawText);
        if (text.length() < limits.minCharacters()) {
            throw new BadRequestException(
                    ("That looks like part of a job description. Paste the whole posting — at least "
                            + "%d characters — so the requirements are in there to match against.")
                            .formatted(limits.minCharacters()));
        }
        return PlainText.truncate(text, limits.maxCharacters());
    }

    /** Keeps one account from filling the table. The delete endpoint is how you make room. */
    private void enforceQuota(AuthenticatedUser caller) {
        long held = postings.countByUserId(caller.id());
        if (held >= limits.maxPerUser()) {
            throw new ConflictException(
                    ("You have reached the limit of %d saved job descriptions. Delete one to save "
                            + "another.").formatted(limits.maxPerUser()));
        }
    }

    /** Blank is stored as null, so the response omits the key rather than showing an empty chip. */
    private static String companyOf(String company) {
        if (company == null || company.isBlank()) {
            return null;
        }
        return truncate(company.strip(), MAX_TITLE_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    /**
     * A saved posting and whether it already existed.
     *
     * <p>Exists so the controller can answer 201 or 200 without asking the repository a second
     * question. The alternative — a {@code reused} field on the response body — would put the same
     * fact in two places, and HTTP already has a way to say "here is the thing, I did not create
     * it".
     *
     * @param posting the posting, with its parse
     * @param reused  true when this is a posting the user had already saved
     */
    public record Saved(JobDescriptionResponse posting, boolean reused) {
    }
}

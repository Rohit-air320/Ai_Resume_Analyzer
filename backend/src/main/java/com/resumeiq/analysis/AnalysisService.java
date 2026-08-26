package com.resumeiq.analysis;

import com.resumeiq.common.exception.ApiException;
import com.resumeiq.common.exception.ErrorCode;
import com.resumeiq.common.exception.ResourceNotFoundException;
import com.resumeiq.security.AuthenticatedUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Running, reading and deleting analyses.
 *
 * <h2>This class holds no transaction, on purpose</h2>
 *
 * <p>It is the class that calls a language model, and that is exactly why. A create request runs in
 * three stages with three different needs: a short read-only transaction to load the two documents and
 * check ownership ({@link AnalysisDocuments}), then the analysis itself, then a short write transaction
 * ({@link AnalysisWriter}). The middle stage crosses the network to a provider that can take a minute
 * and can fail. Wrapping the whole method in {@code @Transactional} — the obvious thing to do, and what
 * a single-service version of this class would end up doing — would pin a connection from the pool and
 * hold a write transaction open across that call, so a handful of concurrent analyses would exhaust the
 * pool waiting on a third party.
 *
 * <p>Splitting it costs three collaborators and buys a database that does not care how slow the
 * provider is today. It also means the annotations here are honest: Spring's transaction support is a
 * proxy around the bean, so a {@code @Transactional} method called from another method of the same
 * class has no transaction at all, and a service that mixed orchestration with its own transactional
 * reads would be one refactor away from that bug.
 *
 * <h2>Synchronous, with the seams for a queue already cut</h2>
 *
 * <p>Create runs the analysis and returns the finished result. For a two-page resume that is a few
 * hundred milliseconds of computation plus whatever the provider takes, and the honest version of a
 * processing screen is a request in flight rather than a poll loop against a row a worker may not have
 * picked up.
 *
 * <p>What makes that a decision rather than a shortcut is that queueing it would change nothing else.
 * {@code AnalysisStatus} already carries {@code QUEUED} and {@code PROCESSING}, the response already
 * reports a status, and {@link AnalysisWriter#saveFailure} already exists for the case where a failure
 * has to be recorded because there is no request left to answer. A worker would take over the two
 * middle stages and the client would poll {@code GET /api/analyses/{id}} — same schema, same endpoints,
 * same DTOs.
 */
@Service
public class AnalysisService {

    private static final Logger log = LoggerFactory.getLogger(AnalysisService.class);

    /**
     * What the user is told when the analysis itself fails.
     *
     * <p>A fixed sentence, used both as the response message and as the stored failure reason. The
     * cause's own message is not fit for either: it can quote a fragment of the resume, the posting or a
     * provider response, and the exception is logged with its stack trace anyway.
     */
    private static final String FAILURE_MESSAGE =
            "Something went wrong while analysing this resume. Please try again.";

    private final AnalysisDocuments documents;
    private final ResumeAnalyzer analyzer;
    private final AnalysisWriter writer;
    private final AnalysisReader reader;
    private final AnalysisRepository analyses;

    public AnalysisService(AnalysisDocuments documents,
                           ResumeAnalyzer analyzer,
                           AnalysisWriter writer,
                           AnalysisReader reader,
                           AnalysisRepository analyses) {
        this.documents = documents;
        this.analyzer = analyzer;
        this.writer = writer;
        this.reader = reader;
        this.analyses = analyses;
    }

    /**
     * Scores a resume against a posting, stores the result, and returns it.
     *
     * <p>The response is read back out of the database rather than mapped from the outcome in memory.
     * That costs one indexed query and buys a property worth having: {@code POST /api/analyses} and
     * {@code GET /api/analyses/{id}} return the same document, built by the same mapper, so a client
     * can treat them interchangeably and a mistake in the mapping cannot hide in whichever one has
     * fewer tests.
     *
     * @throws ResourceNotFoundException if either document is missing or belongs to another account
     * @throws ApiException              if the analysis itself fails, which means a defect rather than
     *                                   a provider having a bad day — those are absorbed upstream
     */
    public AnalysisResponse create(AuthenticatedUser caller, CreateAnalysisRequest request) {
        AnalysisDocuments.Loaded loaded = documents.load(caller, request);
        long startedAt = System.nanoTime();

        AnalysisOutcome outcome;
        try {
            outcome = analyzer.analyse(loaded.toInput());
        } catch (RuntimeException cause) {
            // Every provider failure is already absorbed by the advice layer, which falls back to the
            // offline writer, so reaching here means something in the engine broke. The row is written
            // anyway: the user asked for an analysis of a specific resume against a specific job and
            // deserves to see that it was attempted and failed, rather than a request that left no
            // trace in their history.
            writer.saveFailure(loaded.owner(), loaded.resume(), loaded.posting(), FAILURE_MESSAGE);
            log.error("Analysis failed for user {} on resume {}",
                    caller.publicId(), loaded.resume().getPublicId(), cause);
            throw new ApiException(ErrorCode.INTERNAL_ERROR, FAILURE_MESSAGE, cause);
        }

        long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000L;
        Analysis saved = writer.save(loaded.owner(), loaded.resume(), loaded.posting(),
                outcome, elapsedMs);
        return reader.read(caller, saved.getPublicId());
    }

    /** The caller's analysis history, newest first. */
    public List<AnalysisSummaryResponse> list(AuthenticatedUser caller) {
        return reader.list(caller);
    }

    /** One analysis in full. 404 if it does not exist or is not the caller's. */
    public AnalysisResponse read(AuthenticatedUser caller, UUID publicId) {
        return reader.read(caller, publicId);
    }

    /**
     * Deletes an analysis and everything hanging off it.
     *
     * <p>No {@code @Transactional} here because the repository method declares its own, which is the
     * one place in this feature where the annotation belongs on the query rather than on a service.
     * The owner is in the delete statement as well as being checked by its result — belt and braces on
     * the one operation where a mistake is not recoverable.
     */
    public void delete(AuthenticatedUser caller, UUID publicId) {
        int removed = analyses.deleteByPublicIdAndUserId(publicId, caller.id());
        if (removed != 1) {
            throw new ResourceNotFoundException("Analysis", publicId);
        }
        log.info("Deleted analysis {} for user {}", publicId, caller.publicId());
    }
}

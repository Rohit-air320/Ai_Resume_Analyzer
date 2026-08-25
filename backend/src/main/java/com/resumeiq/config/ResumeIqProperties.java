package com.resumeiq.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * Type-safe binding for every {@code resumeiq.*} setting.
 *
 * <p>Validated at startup, so a missing or malformed value fails fast with a clear
 * message instead of surfacing as a null pointer during the first request.
 *
 * @param app  application identity reported by the health endpoint
 * @param cors browser origins permitted to call this API
 * @param seed reference data loaded at startup
 * @param auth token lifetimes, cookie flags and login throttling
 * @param upload where resume files are stored and the limits applied to them
 * @param posting limits applied to a pasted job description and to what is read out of it
 * @param ai which model writes the advice, and the budgets around the call
 */
@Validated
@ConfigurationProperties(prefix = "resumeiq")
public record ResumeIqProperties(
        @Valid @NotNull App app,
        @Valid @NotNull Cors cors,
        @Valid @NotNull Seed seed,
        @Valid @NotNull Auth auth,
        @Valid @NotNull Upload upload,
        @Valid @NotNull Posting posting,
        @Valid @NotNull Ai ai
) {

    /**
     * @param name    display name of the product
     * @param version version reported by {@code GET /api/health}
     */
    public record App(@NotBlank String name, @NotBlank String version) {
    }

    /**
     * @param allowedOrigins exact browser origins allowed to call the API.
     *                       Wildcards are intentionally unsupported: credentialed
     *                       requests (Phase 3) require explicit origins.
     */
    public record Cors(@NotEmpty List<String> allowedOrigins) {
    }

    /**
     * @param skills whether to load the skill taxonomy from {@code data/skills.json} on startup.
     *               On by default because an empty taxonomy silently degrades every analysis;
     *               switchable off for tests that want to control the table themselves.
     */
    public record Seed(@NotNull Boolean skills) {
    }

    /**
     * Everything the authentication phase needs to be tuned without a rebuild.
     *
     * @param jwtSecret          HMAC key for access tokens. Deliberately not {@code @NotBlank}:
     *                           an empty value is legal in development, where
     *                           {@code JwtService} generates a throwaway key rather than let a
     *                           default secret exist anywhere in this repository. Outside
     *                           development a blank value stops startup.
     * @param accessTokenMinutes access-token lifetime. Short by design — the token lives in
     *                           browser memory and is refreshed silently, so the cost of a
     *                           leaked one is bounded by this number.
     * @param refreshTokenDays   how long a session can be resumed without re-entering a
     *                           password. Each use rotates the token, so this is a cap on
     *                           inactivity rather than on how long one token is valid.
     * @param bcryptStrength     BCrypt cost factor. 12 is roughly a quarter of a second per
     *                           hash on a laptop, which is the point; tests override it to 4
     *                           so a suite that logs in fifty times still runs in seconds.
     * @param refreshCookieName  name of the httpOnly cookie carrying the refresh token
     * @param refreshCookieSameSite {@code Lax}, {@code Strict} or {@code None}. {@code Lax} is
     *                           what makes the refresh endpoint safe without a CSRF token: a
     *                           cross-site POST does not carry the cookie at all.
     * @param refreshCookieSecure whether to mark the cookie {@code Secure}. False in local
     *                           development because {@code http://localhost} is not a secure
     *                           context; true everywhere else.
     * @param maxLoginAttempts   failed attempts allowed per email and per IP before the
     *                           lockout window starts
     * @param lockoutMinutes     how long a locked-out email or address must wait
     */
    public record Auth(
            String jwtSecret,
            @Positive Integer accessTokenMinutes,
            @Positive Integer refreshTokenDays,
            @Min(4) Integer bcryptStrength,
            @NotBlank String refreshCookieName,
            @Pattern(regexp = "Lax|Strict|None") String refreshCookieSameSite,
            @NotNull Boolean refreshCookieSecure,
            @Positive Integer maxLoginAttempts,
            @Positive Integer lockoutMinutes
    ) {

        /** HS256 needs a 256-bit key, and jjwt refuses anything shorter. */
        public static final int MINIMUM_SECRET_LENGTH = 32;

        /** True when the environment supplied a key long enough to sign with. */
        public boolean hasUsableSecret() {
            return jwtSecret != null && jwtSecret.strip().length() >= MINIMUM_SECRET_LENGTH;
        }
    }

    /**
     * Resume storage and the limits applied to an upload.
     *
     * <p>Every one of these is a limit rather than a feature, which is the point: an
     * upload endpoint without bounds is a disk-exhaustion primitive that anyone with an
     * account can reach.
     *
     * @param storageDir             directory that holds uploaded files. Relative paths resolve
     *                               against the working directory, which is what makes the
     *                               default usable straight from a clone. Files here are named
     *                               by the server and never by the uploader.
     * @param maxFileSize            largest accepted file, as a {@code DataSize} so it can be
     *                               written "5MB" and read from the same {@code MAX_UPLOAD_SIZE}
     *                               variable as {@code spring.servlet.multipart.max-file-size}.
     *                               Two limits configured from one value cannot drift apart.
     *                               Enforced twice on purpose — the container rejects the body
     *                               before reading it all, and {@code ResumeService} checks again,
     *                               because a service must not assume a polite caller.
     * @param minExtractedCharacters shortest extraction treated as usable text. A scanned
     *                               resume is a picture of words: it parses perfectly and
     *                               yields almost nothing, and failing here with an explanation
     *                               is far kinder than scoring an empty document.
     * @param maxExtractedCharacters cap on stored text. Guards the {@code LONGTEXT} column and,
     *                               later, the model's context window; a resume long enough to
     *                               hit this has other problems.
     * @param maxResumesPerUser      how many resumes one account may keep at once. A quota, not
     *                               a judgement — the delete endpoint makes room.
     */
    public record Upload(
            @NotBlank String storageDir,
            @NotNull DataSize maxFileSize,
            @Positive Integer minExtractedCharacters,
            @Positive Integer maxExtractedCharacters,
            @Positive Integer maxResumesPerUser
    ) {

        /** Convenience for the size comparison, which is done in bytes. */
        public long maxFileSizeBytes() {
            return maxFileSize.toBytes();
        }

        /** Used in the "file too large" message, where a byte count would be unhelpful. */
        public long maxFileSizeMegabytes() {
            return Math.max(1, maxFileSize.toMegabytes());
        }
    }

    /**
     * Limits on a pasted job posting, and on how much is read out of one.
     *
     * <p>A posting arrives as text in a JSON body rather than as a file, which removes the
     * file-shaped problems and leaves the size one: a request body is still untrusted input,
     * and "paste your job description" is an invitation to paste a novel.
     *
     * @param minCharacters shortest text accepted. A posting shorter than this is nearly always
     *                      a partial paste — someone copied the job title and lost the rest —
     *                      and matching a resume against three lines produces a number that
     *                      looks authoritative and means nothing.
     * @param maxCharacters cap on stored text, cutting at a line break. Postings that run long
     *                      do it with boilerplate at the end (benefits, equal-opportunity
     *                      statements, application instructions), so the requirements survive
     *                      the cut. Also bounds the Phase 6 prompt.
     * @param maxPerUser    how many postings one account may keep. Higher than the resume quota
     *                      because the natural loop is one resume against many postings.
     * @param maxKeywords   how many ranked keywords to return. A ceiling on advice, not on
     *                      data: a list of two hundred "important keywords" is not something a
     *                      person can act on, and presenting it as a checklist is exactly the
     *                      nudge toward keyword stuffing this product refuses to give.
     */
    public record Posting(
            @Positive Integer minCharacters,
            @Positive Integer maxCharacters,
            @Positive Integer maxPerUser,
            @Positive Integer maxKeywords
    ) {
    }

    /**
     * The model that writes the advice, and the budgets around the call.
     *
     * <p>Read by the backend only. The key never reaches a browser, never appears in a response
     * body, and is never logged — {@link #describe()} exists so that startup can say which
     * provider is active without saying what the credential is.
     *
     * <p>Note what is <em>not</em> here: nothing that changes a score. Every number this product
     * reports is computed in Java from the resume and the posting, so the same pair always scores
     * the same, the scores survive the provider being down, and each one can be explained line by
     * line. The model writes prose. {@link #scoreTolerance} is the one score-adjacent setting and
     * it only decides when a disagreement is worth a log line.
     *
     * @param provider      {@code mock} or {@code anthropic}. {@code mock} is the default so a
     *                      fresh clone runs the whole product, and the whole test suite, with no
     *                      credential — the offline writer produces real advice from the
     *                      deterministic findings rather than canned text.
     * @param apiKey        provider credential. Blank means the same as {@code mock}: a missing
     *                      key is a configuration state, not a crash, and refusing to start would
     *                      make the AI a hard dependency of a product that is designed not to
     *                      need one.
     * @param baseUrl       provider endpoint, configurable so a proxy or a gateway can be put in
     *                      front without a rebuild
     * @param model         model identifier sent with each request, and stored on the analysis so
     *                      an old result can say what wrote it
     * @param timeoutSeconds how long to wait for a completion. Generous, because a long resume
     *                      against a long posting is a real amount of reading, and bounded,
     *                      because a request that never returns holds a servlet thread.
     * @param maxOutputTokens ceiling on the response. The advice is a page of text, so this is
     *                      sized for that plus the JSON scaffolding around it.
     * @param maxRetries    how many times a transient failure (429, 5xx, timeout) is retried.
     *                      Small on purpose: this call sits inside a user's request, and the
     *                      person is watching a progress screen.
     * @param maxPromptCharacters how much resume and posting text may go into one prompt. The
     *                      inputs are already capped when they are stored, but a prompt is
     *                      assembled from several of them and the sum is what costs money.
     * @param scoreTolerance how far the model's own scores may differ from the computed ones
     *                      before the disagreement is logged. The computed number always wins;
     *                      this decides when a gap is large enough to be worth investigating.
     */
    public record Ai(
            @NotBlank String provider,
            String apiKey,
            @NotBlank String baseUrl,
            @NotBlank String model,
            @Positive Integer timeoutSeconds,
            @Positive Integer maxOutputTokens,
            @Min(0) Integer maxRetries,
            @Positive Integer maxPromptCharacters,
            @Min(0) Integer scoreTolerance
    ) {

        /** The provider name that means "no provider": deterministic advice, no network, no key. */
        public static final String MOCK = "mock";

        /** True when a real model should be called. */
        public boolean callsAModel() {
            return !MOCK.equalsIgnoreCase(provider.strip()) && hasKey();
        }

        /** True when a credential was supplied at all. */
        public boolean hasKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /**
         * What to log at startup. Says which provider and model are active and whether a key was
         * found; never says anything about the key itself.
         */
        public String describe() {
            if (MOCK.equalsIgnoreCase(provider.strip())) {
                return "mock (offline writer, no network calls)";
            }
            return hasKey()
                    ? provider + " / " + model
                    : provider + " / " + model + " — no API key set, falling back to the offline writer";
        }
    }
}

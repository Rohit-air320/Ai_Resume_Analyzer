package com.resumeiq.support;

import com.resumeiq.config.ResumeIqProperties;
import com.resumeiq.config.ResumeIqProperties.Ai;
import com.resumeiq.config.ResumeIqProperties.App;
import com.resumeiq.config.ResumeIqProperties.Auth;
import com.resumeiq.config.ResumeIqProperties.Cors;
import com.resumeiq.config.ResumeIqProperties.Posting;
import com.resumeiq.config.ResumeIqProperties.Seed;
import com.resumeiq.config.ResumeIqProperties.Upload;
import org.springframework.util.unit.DataSize;

import java.util.List;

/**
 * One place that knows how to build {@link ResumeIqProperties} by hand.
 *
 * <p>This class exists because of a bug that has now happened twice. {@code ResumeIqProperties}
 * is a record, and a record's canonical constructor takes every component — so adding one
 * setting breaks every test that constructs the object directly, with a compile error in files
 * that have nothing to do with the new setting. Spring itself is unaffected: it binds from YAML.
 * Only the hand-built copies in tests break.
 *
 * <p>So the tests get one hand-built copy. A new component is one edit here and a default that
 * every existing test silently inherits, instead of a scavenger hunt through {@code src/test}
 * for call sites the compiler will find one at a time.
 *
 * <p>Note that {@code new ResumeIqProperties(...)} appears exactly once below, in {@link #build}.
 * The named factories all delegate to it. Four copies of the constructor call would have made
 * this class four edits per new setting rather than one, which is most of the problem it was
 * written to remove.
 *
 * <p>Values are chosen to be safe rather than realistic. BCrypt runs at cost 4 because a suite
 * that hashes fifty passwords at production cost takes a minute; seeding is off so no test
 * writes the skill catalogue into a shared H2 schema; and the storage directory is under
 * {@code build/}, which is not on the classpath and is deleted by {@code mvn clean}.
 */
public final class TestProperties {

    /** Under build/ so it is disposable, and gitignored along with the rest of build output. */
    public static final String STORAGE_DIR = "./build/test-uploads";

    /**
     * The provider every test uses unless it says otherwise.
     *
     * <p>No test in this suite may reach the network. That is not only about speed: a suite whose
     * result depends on a credential and a third party is a suite that fails for reasons unrelated
     * to the code, and one that nobody can run on a fresh clone.
     */
    public static final String MOCK_PROVIDER = ResumeIqProperties.Ai.MOCK;

    private TestProperties() {
    }

    /** Everything at its test default. */
    public static ResumeIqProperties defaults() {
        return build(new Seed(false), auth(""), upload(STORAGE_DIR), posting(), ai(MOCK_PROVIDER, ""));
    }

    /** Defaults, with the authentication block replaced — token and lockout tests. */
    public static ResumeIqProperties withAuth(Auth auth) {
        return build(new Seed(false), auth, upload(STORAGE_DIR), posting(), ai(MOCK_PROVIDER, ""));
    }

    /** Defaults, with the upload block replaced — storage and extraction tests. */
    public static ResumeIqProperties withUpload(Upload upload) {
        return build(new Seed(false), auth(""), upload, posting(), ai(MOCK_PROVIDER, ""));
    }

    /** Defaults, with the posting block replaced — job-description limit tests. */
    public static ResumeIqProperties withPosting(Posting posting) {
        return build(new Seed(false), auth(""), upload(STORAGE_DIR), posting, ai(MOCK_PROVIDER, ""));
    }

    /** Defaults, with catalogue seeding switched on or off — the seeder's own tests. */
    public static ResumeIqProperties withSeedSkills(boolean seedSkills) {
        return build(new Seed(seedSkills), auth(""), upload(STORAGE_DIR), posting(),
                ai(MOCK_PROVIDER, ""));
    }

    /** Defaults, with the AI block replaced — provider, prompt-budget and advice tests. */
    public static ResumeIqProperties withAi(Ai ai) {
        return build(new Seed(false), auth(""), upload(STORAGE_DIR), posting(), ai);
    }

    /**
     * @param jwtSecret HMAC key; empty means "no usable secret", which is a case the token
     *                  service is expected to handle rather than a mistake
     */
    public static Auth auth(String jwtSecret) {
        return new Auth(jwtSecret, 15, 7, 4, "resumeiq_rt", "Lax", false, 5, 15);
    }

    /** Upload limits at their defaults, rooted wherever the caller wants. */
    public static Upload upload(String storageDir) {
        return upload(storageDir, 200, 20_000);
    }

    /**
     * @param minCharacters extraction floor — set low to accept a short fixture, high to force
     *                      the "almost no text" refusal
     * @param maxCharacters storage ceiling — set small to force truncation
     */
    public static Upload upload(String storageDir, int minCharacters, int maxCharacters) {
        return new Upload(storageDir, DataSize.ofMegabytes(5), minCharacters, maxCharacters, 20);
    }

    /** Posting limits at their production defaults. */
    public static Posting posting() {
        return posting(200, 20_000);
    }

    /**
     * The AI block, with the budgets small enough to be reasoned about in a test.
     *
     * @param provider {@link #MOCK_PROVIDER} for the offline writer, or a real provider name when
     *                 the test supplies its own stub transport
     * @param apiKey   blank means "no credential", which is a supported state rather than an error
     */
    public static Ai ai(String provider, String apiKey) {
        return ai(provider, apiKey, 1);
    }

    /**
     * The AI block with a chosen retry budget.
     *
     * @param maxRetries retries after the first attempt, so zero means "one attempt and then the
     *                   offline writer". The advice-source tests need both ends of this: one attempt
     *                   to check that a failure falls back at all, and several to check that a
     *                   transient failure is retried rather than given up on.
     */
    public static Ai ai(String provider, String apiKey, int maxRetries) {
        // (provider, apiKey, baseUrl, model, timeoutSeconds, maxOutputTokens, maxRetries,
        //  maxPromptCharacters, scoreTolerance). Named here because six of the nine are strings or
        // small integers, and any two of those swapped would still compile.
        return new Ai(provider, apiKey, "https://ai.example.test", "test-model",
                5, 2_000, maxRetries, 8_000, 15);
    }

    /**
     * @param minCharacters length floor — set low to accept a two-line fixture, high to force
     *                      the "that is not a job description" refusal
     * @param maxCharacters length ceiling — set small to force truncation
     */
    public static Posting posting(int minCharacters, int maxCharacters) {
        return posting(minCharacters, maxCharacters, 25);
    }

    /**
     * @param maxKeywords how many keywords the parser may return — set to a handful to check the
     *                    cap is applied after ranking rather than before it
     */
    public static Posting posting(int minCharacters, int maxCharacters, int maxKeywords) {
        // Component order is (min, max, maxPerUser, maxKeywords). Worth naming, because the last
        // two are both small integers and swapping them compiles.
        return new Posting(minCharacters, maxCharacters, 50, maxKeywords);
    }

    /** The only place in the test sources that calls the canonical constructor. */
    private static ResumeIqProperties build(Seed seed, Auth auth, Upload upload, Posting posting,
                                            Ai ai) {
        return new ResumeIqProperties(app(), cors(), seed, auth, upload, posting, ai);
    }

    private static App app() {
        return new App("ResumeIQ", "0.1.0");
    }

    private static Cors cors() {
        return new Cors(List.of("http://example.test"));
    }
}

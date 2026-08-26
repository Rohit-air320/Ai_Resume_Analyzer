package com.resumeiq.support;

import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A full-context HTTP test for the analysis endpoints.
 *
 * <p>Everything Phase 7 added is a collaboration: a token, a filter chain, two owned rows, a scoring
 * engine, an advice writer, five child tables and a mapper that has to agree with all of them. Mocking
 * any part of that produces a test that passes while the endpoint returns the wrong document, which is
 * the failure this phase is most exposed to — the create path and the read path share one mapper
 * precisely so that one class of bug shows up in both.
 *
 * <p>It exists as an annotation rather than as copied property blocks so the classes that use it share
 * one Spring context. Spring's test framework caches contexts by their merged configuration, so an
 * identical annotation is a cache hit and a single differing property is a second application startup.
 * With the skill catalogue seeded that is several seconds a class.
 *
 * <p>Four overrides, each for a reason:
 *
 * <p><strong>Its own database.</strong> These tests drive HTTP, and HTTP commits — there is no test
 * transaction around a MockMvc call to roll back. Rows left in the shared dev schema would turn up
 * inside repository slices that never asked for them.
 *
 * <p><strong>The real catalogue.</strong> The promise this feature makes is "we found Java in your
 * resume and Docker missing from it", and that promise is kept by the shipped catalogue agreeing with
 * the parser. A Java match found because the test inserted Java proves considerably less.
 *
 * <p><strong>BCrypt at cost 4.</strong> Production uses 12. Correct there, and a minute of nothing
 * across a suite that signs in this often.
 *
 * <p><strong>Uploads under {@code build/}.</strong> The analysis path needs a real stored resume, so
 * these tests write files. Disposable, off the classpath, and deleted when the class finishes.
 *
 * <p>Note what is <em>not</em> overridden: the AI provider. It defaults to {@code mock}, so the offline
 * writer produces the prose and the suite needs no key and no network. A test suite that requires a
 * credential is a test suite nobody runs.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:resumeiq-analyses;MODE=MySQL;DATABASE_TO_LOWER=TRUE;"
                + "CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        "resumeiq.seed.skills=true",
        "resumeiq.auth.bcrypt-strength=4",
        "resumeiq.upload.storage-dir=" + AnalysisIntegrationTest.STORAGE_DIR,
})
public @interface AnalysisIntegrationTest {

    /** Referenced from the annotation above, so it has to be a compile-time constant. */
    String STORAGE_DIR = "./build/test-uploads/analysis-api";
}

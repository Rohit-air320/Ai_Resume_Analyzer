package com.resumeiq.support;

import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A JPA slice test against the development database.
 *
 * <p>{@code replace = NONE} is the point of this annotation. Left to itself, {@code @DataJpaTest}
 * swaps the configured datasource for a plain {@code jdbc:h2:mem:<random>} — H2 in its own
 * dialect, not MySQL's. Half of what these tests exist to check is MySQL-specific: reserved
 * words, unique indexes over nullable columns, {@code LONGTEXT} mapping. Keeping the dev
 * datasource keeps H2 in {@code MODE=MySQL}, so a test that passes here means something.
 *
 * <p>Each test still runs in a transaction that is rolled back afterwards, so the shared
 * in-memory database stays clean between classes.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@DataJpaTest
@ActiveProfiles("dev")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public @interface RepositoryTest {
}

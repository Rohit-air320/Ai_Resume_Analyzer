package com.resumeiq.user;

import com.resumeiq.support.RepositoryTest;
import com.resumeiq.support.TestFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Account lookup, and the one invariant the whole auth phase rests on: an email address
 * identifies exactly one account, whatever case it was typed in.
 */
@RepositoryTest
class UserRepositoryTest {

    @Autowired
    private UserRepository users;

    @Autowired
    private TestEntityManager em;

    @Test
    @DisplayName("the builder path normalises the email too, not just the setter")
    void normalisesEmailOnEveryWritePath() {
        // Lombok's builder writes fields directly, so setEmail() is bypassed here. If the
        // @PrePersist callback were removed, this row would be stored as "  ROHIT@Example.COM "
        // and the login lookup below would miss it.
        users.saveAndFlush(User.builder()
                .email("  ROHIT@Example.COM ")
                .passwordHash(TestFixtures.PASSWORD_HASH)
                .fullName("Rohit")
                .role(Role.USER)
                .build());
        em.clear();

        assertThat(users.findByEmail("rohit@example.com")).isPresent();
        assertThat(users.findByEmailNormalized("  Rohit@EXAMPLE.com  ")).isPresent();
        assertThat(users.existsByEmailNormalized("ROHIT@example.COM")).isTrue();
        assertThat(users.findByEmail("someone.else@example.com")).isEmpty();
    }

    @Test
    @DisplayName("register() sets the default role and keeps the given name")
    void registerAppliesDefaults() {
        User saved = users.saveAndFlush(TestFixtures.user("defaults@example.com"));

        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.getFullName()).isEqualTo("Test Person");
        assertThat(saved.getEmail()).isEqualTo("defaults@example.com");
        assertThat(saved.getLastLoginAt()).isNull();
        assertThat(saved.getPublicId()).isNotNull();
    }

    @Test
    @DisplayName("an account is addressable by its public id")
    void findsByPublicId() {
        User saved = users.saveAndFlush(TestFixtures.user("publicid-user@example.com"));
        em.clear();

        assertThat(users.findByPublicId(saved.getPublicId()))
                .get()
                .extracting(User::getEmail)
                .isEqualTo("publicid-user@example.com");
    }

    @Test
    @DisplayName("the same address in different case cannot register twice")
    void rejectsDuplicateEmailWhateverTheCase() {
        users.saveAndFlush(TestFixtures.user("dup@example.com"));
        em.clear();

        // On MySQL the default collation would catch this on its own; on H2 it would not. The
        // normalisation is what makes the constraint behave the same on both.
        assertThatThrownBy(() -> users.saveAndFlush(TestFixtures.user("DUP@Example.COM")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}

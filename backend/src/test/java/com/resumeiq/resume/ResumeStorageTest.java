package com.resumeiq.resume;

import com.resumeiq.resume.extract.FileType;
import com.resumeiq.support.TestProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The file side of an upload, on a real filesystem in a temporary directory.
 *
 * <p>The behaviour under test is mostly an absence. No part of a stored path comes from the
 * uploader — {@link #namesFilesItself()} is the assertion that matters most in this class,
 * because it is what makes path traversal impossible here rather than merely filtered for.
 * A file called {@code ../../../etc/passwd.pdf} is stored under a UUID like everything else,
 * and there is no sanitising code to get wrong.
 */
class ResumeStorageTest {

    /** Package-private: JUnit refuses to inject a temporary directory into a private field. */
    @TempDir
    Path storageRoot;

    private ResumeStorage storage;

    @BeforeEach
    void setUp() {
        storage = new ResumeStorage(TestProperties.withUpload(
                TestProperties.upload(storageRoot.toString())));
        // Normally Spring calls this. Calling it by hand is also the test that it is idempotent
        // enough to survive a directory that already exists, which after the first run it does.
        storage.prepareStorageDirectory();
    }

    @Test
    @DisplayName("the storage directory is created at startup, not on first upload")
    void createsItsDirectory() {
        Path nested = storageRoot.resolve("does/not/exist/yet");
        ResumeStorage fresh = new ResumeStorage(
                TestProperties.withUpload(TestProperties.upload(nested.toString())));

        fresh.prepareStorageDirectory();

        // A misconfigured path should be a startup failure found by whoever is deploying, not a
        // 500 for the first person who tries to upload something.
        assertThat(Files.isDirectory(nested)).isTrue();
        assertThat(fresh.root()).isAbsolute();
    }

    @Test
    @DisplayName("keys are a date shard, a UUID and the real extension")
    void namesFilesItself() {
        String key = storage.newStorageKey(FileType.PDF);

        String expectedShard = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
        assertThat(key).matches(
                expectedShard + "/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.pdf");
        assertThat(storage.newStorageKey(FileType.DOCX)).endsWith(".docx");
    }

    @Test
    @DisplayName("two keys are never the same key")
    void generatesDistinctKeys() {
        assertThat(storage.newStorageKey(FileType.PDF))
                .isNotEqualTo(storage.newStorageKey(FileType.PDF));
    }

    @Test
    @DisplayName("stored bytes come back byte for byte, in a sharded directory")
    void storesAndFinds() {
        byte[] content = "%PDF-1.7 pretend resume".getBytes(StandardCharsets.UTF_8);
        String key = storage.newStorageKey(FileType.PDF);

        storage.store(content, key);

        assertThat(storage.exists(key)).isTrue();
        Path written = storageRoot.resolve(key);
        assertThat(written).exists();
        assertThat(readAllBytes(written)).isEqualTo(content);
        // Sharded by year and month: a flat directory eventually holds hundreds of thousands of
        // files, which is a filesystem problem long before it is a code problem.
        assertThat(written.getParent().getParent().getParent()).isEqualTo(storage.root());
    }

    @Test
    @DisplayName("delete removes the file and says whether there was one")
    void deletesAFile() {
        String key = storage.newStorageKey(FileType.PDF);
        storage.store("%PDF-1.7".getBytes(StandardCharsets.UTF_8), key);

        assertThat(storage.delete(key)).isTrue();
        assertThat(storage.exists(key)).isFalse();
    }

    @Test
    @DisplayName("deleting a file that is already gone is not an error")
    void toleratesAMissingFile() {
        // A delete that half-succeeded must be able to finish, and a storage directory somebody
        // wiped by hand must not leave rows that cannot be removed.
        assertThat(storage.delete(storage.newStorageKey(FileType.PDF))).isFalse();
    }

    @Test
    @DisplayName("writing twice to one key fails instead of overwriting a resume")
    void refusesToOverwrite() {
        String key = storage.newStorageKey(FileType.PDF);
        storage.store("first".getBytes(StandardCharsets.UTF_8), key);

        // Unreachable with random UUIDs. The point is that if it ever happened it would be an
        // error and not the silent loss of somebody else's document.
        assertThatThrownBy(() -> storage.store("second".getBytes(StandardCharsets.UTF_8), key))
                .isInstanceOf(UncheckedIOException.class);
    }

    @Test
    @DisplayName("a key that climbs out of the storage root is refused")
    void refusesTraversal() {
        // Keys are generated here and read back from the database, so this cannot fire in normal
        // operation. It is the difference between a failed request and an arbitrary file being
        // read or deleted on the day something else writes to that column.
        for (String hostile : new String[] {
                "../../../etc/passwd",
                "2026/08/../../../../etc/passwd.pdf",
                "/etc/passwd",
        }) {
            assertThatThrownBy(() -> storage.exists(hostile))
                    .as("key %s", hostile)
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("outside the storage root");
        }
    }

    @Test
    @DisplayName("a blank key is refused before it becomes the storage root itself")
    void refusesABlankKey() {
        // "" resolves to the root directory, which exists — so without this check exists("")
        // would answer true and delete("") would try to remove the whole tree.
        assertThatThrownBy(() -> storage.exists(""))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> storage.delete(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static byte[] readAllBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}

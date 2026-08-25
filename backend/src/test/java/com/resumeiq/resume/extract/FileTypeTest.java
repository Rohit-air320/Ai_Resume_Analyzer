package com.resumeiq.resume.extract;

import com.resumeiq.support.DocumentFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Format detection, which is where an upload endpoint is usually attacked.
 *
 * <p>The filename and the {@code Content-Type} header are both chosen by whoever is
 * uploading, so neither appears in this class or in these tests. Every case below is
 * decided on content alone — which is why the fixtures are real documents built by the same
 * libraries that will later read them, rather than strings that happen to start with the
 * right five bytes.
 */
class FileTypeTest {

    @Test
    @DisplayName("a real PDF is recognised from its header")
    void sniffsPdf() {
        byte[] content = DocumentFixtures.pdf("PRIYA SHARMA", "Java developer");

        assertThat(FileType.sniff(content)).contains(FileType.PDF);
    }

    @Test
    @DisplayName("a real DOCX is recognised from its zip header")
    void sniffsDocx() {
        byte[] content = DocumentFixtures.docx("PRIYA SHARMA", "Java developer");

        assertThat(FileType.sniff(content)).contains(FileType.DOCX);
    }

    @Test
    @DisplayName("a text file is not a resume format, whatever it is called")
    void refusesPlainText() {
        assertThat(FileType.sniff(DocumentFixtures.plainText())).isEmpty();
    }

    @Test
    @DisplayName("a pre-2007 .doc is unsupported, and identifiable enough to say why")
    void identifiesLegacyWordDocuments() {
        byte[] content = DocumentFixtures.legacyDoc();

        // Not a supported type — but recognised, so the refusal can name the fix instead of
        // leaving somebody guessing what is wrong with their file.
        assertThat(FileType.sniff(content)).isEmpty();
        assertThat(FileType.isLegacyWordDocument(content)).isTrue();
        assertThat(FileType.isLegacyWordDocument(DocumentFixtures.pdf("x"))).isFalse();
    }

    @Test
    @DisplayName("nothing, or nearly nothing, is not a format")
    void refusesEmptyAndTruncatedContent() {
        assertThat(FileType.sniff(null)).isEmpty();
        assertThat(FileType.sniff(new byte[0])).isEmpty();
        // The first two bytes of "%PDF-" and no more. A signature check that read past the
        // end of a short array would throw here instead of answering.
        assertThat(FileType.sniff(new byte[] {0x25, 0x50})).isEmpty();
        assertThat(FileType.isLegacyWordDocument(new byte[] {(byte) 0xD0, (byte) 0xCF})).isFalse();
    }

    @Test
    @DisplayName("a file whose contents lie about its name is judged by its contents")
    void ignoresWhatTheFileClaims() {
        // This is the whole point of the class: these bytes would arrive named "resume.pdf"
        // with Content-Type application/pdf, and they are still a DOCX.
        Optional<FileType> sniffed = FileType.sniff(DocumentFixtures.docx("PRIYA SHARMA"));

        // An Optional holds one value, so this is the whole assertion: it is DOCX, and
        // therefore not the PDF that both the filename and the Content-Type claimed.
        assertThat(sniffed).contains(FileType.DOCX);
    }

    @Test
    @DisplayName("each type carries the canonical media type and extension we store")
    void describesItself() {
        assertThat(FileType.PDF.contentType()).isEqualTo("application/pdf");
        assertThat(FileType.PDF.extension()).isEqualTo("pdf");
        assertThat(FileType.PDF.label()).isEqualTo("PDF");

        assertThat(FileType.DOCX.contentType())
                .isEqualTo("application/vnd.openxmlformats-officedocument.wordprocessingml.document");
        assertThat(FileType.DOCX.extension()).isEqualTo("docx");
        assertThat(FileType.DOCX.label()).isEqualTo("DOCX");
    }

    @Test
    @DisplayName("the accepted-formats list names every type and nothing else")
    void listsWhatItAccepts() {
        // The list appears in error copy and in the file picker's accept attribute, so it
        // going stale is a lie told to users rather than a broken build.
        assertThat(FileType.supportedExtensions()).isEqualTo("PDF, DOCX");
        for (FileType type : FileType.values()) {
            assertThat(FileType.supportedExtensions()).contains(type.label());
        }
    }
}

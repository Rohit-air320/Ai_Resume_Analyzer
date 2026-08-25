package com.resumeiq.resume.extract;

import com.resumeiq.support.DocumentFixtures;
import com.resumeiq.support.TestProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The two rules that apply to every format, and the wiring that picks a format's reader.
 *
 * <p>Both rules live here rather than in the extractors because both are the same judgement
 * whichever file arrived. A floor, because a document that parses cleanly and yields nothing
 * is almost always a scan, and scoring it would produce a confident number about an empty
 * string. A ceiling, applied by truncation and not refusal, because a 200-page PDF is
 * somebody's mistake rather than an attack.
 */
class TextExtractionServiceTest {

    private final TextExtractionService service = serviceWith(200, 40_000);

    @Test
    @DisplayName("a PDF is routed to the PDF reader and comes back as text")
    void extractsPdf() {
        byte[] content = DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines());

        ExtractedText extracted = service.extract(FileType.PDF, content);

        assertThat(extracted.text()).contains("PRIYA SHARMA");
        assertThat(extracted.pageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("every supported format has a reader behind it")
    void coversEveryFormat() {
        // An exhaustive switch with no default: adding a FileType stops this file compiling
        // until somebody says what a fixture for it looks like, which is the cheapest possible
        // reminder that a new format needs a reader bean as well as an enum constant.
        for (FileType type : FileType.values()) {
            byte[] content = switch (type) {
                case PDF -> DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines());
                case DOCX -> DocumentFixtures.docx(DocumentFixtures.realisticResumeLines());
            };

            assertThat(service.extract(type, content).text())
                    .as("text extracted from a %s file", type.label())
                    .contains("PRIYA SHARMA");
        }
    }

    @Test
    @DisplayName("a scan is refused with an explanation of what to upload instead")
    void refusesADocumentWithAlmostNoText() {
        // A photograph of a resume parses perfectly and says nothing. The message has to explain
        // that, because from the uploader's point of view the file is obviously full of words.
        assertThatThrownBy(() -> service.extract(FileType.PDF, DocumentFixtures.pdfWithNoText()))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("Almost no text")
                .hasMessageContaining("scan");
    }

    @Test
    @DisplayName("the floor is configuration, not a constant")
    void appliesTheConfiguredFloor() {
        byte[] content = DocumentFixtures.pdf("Java developer");

        // The same file: too short for the default floor, long enough once the floor is lowered.
        assertThatThrownBy(() -> service.extract(FileType.PDF, content))
                .isInstanceOf(UnreadableResumeException.class);
        assertThat(serviceWith(5, 40_000).extract(FileType.PDF, content).text())
                .contains("Java developer");
    }

    @Test
    @DisplayName("an over-long document is truncated rather than refused")
    void truncatesInsteadOfRefusing() {
        byte[] content = DocumentFixtures.pdfWithPages(4, DocumentFixtures.realisticResumeLines());

        ExtractedText extracted = serviceWith(200, 400).extract(FileType.PDF, content);

        // Refusing here would mean telling someone their resume is too long to look at. The cap
        // exists to bound what is stored and what is later sent to the model, not to judge.
        assertThat(extracted.text().length()).isLessThanOrEqualTo(400);
        assertThat(extracted.text()).contains("PRIYA SHARMA");
        assertThat(extracted.pageCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("two readers claiming one format is a startup failure")
    void refusesAmbiguousExtractors() {
        // Left alone this would make behaviour depend on bean ordering: the kind of bug that
        // reproduces on one machine and not another, and never in a test.
        assertThatThrownBy(() -> new TextExtractionService(
                List.of(new PdfTextExtractor(), new PdfTextExtractor()),
                TestProperties.defaults()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Two extractors registered for PDF");
    }

    @Test
    @DisplayName("a format with no reader fails as an unreadable file, not a null pointer")
    void handlesAMissingExtractor() {
        TextExtractionService incomplete = new TextExtractionService(
                List.of(new PdfTextExtractor()), TestProperties.defaults());

        assertThatThrownBy(() -> incomplete.extract(FileType.DOCX, DocumentFixtures.docx("PRIYA SHARMA")))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("No reader is available");
    }

    private static TextExtractionService serviceWith(int minCharacters, int maxCharacters) {
        // The real extractor beans, constructed directly. They have no dependencies, so a
        // Spring context here would buy nothing but seconds.
        return new TextExtractionService(
                List.of(new PdfTextExtractor(), new DocxTextExtractor()),
                TestProperties.withUpload(TestProperties.upload(
                        TestProperties.STORAGE_DIR, minCharacters, maxCharacters)));
    }
}

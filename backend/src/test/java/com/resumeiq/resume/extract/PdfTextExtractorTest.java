package com.resumeiq.resume.extract;

import com.resumeiq.common.exception.ErrorCode;
import com.resumeiq.support.DocumentFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * PDF extraction, against PDFs rather than against a mock of one.
 *
 * <p>Mocking PDFBox here would test that this class calls the methods I decided it calls,
 * which is the same claim written twice. The failures worth covering — a document locked
 * against copying, one that needs a password, one whose bytes stop halfway — are all
 * PDFBox's behaviour, and the only way to know how it reports them is to hand it a real
 * file and see.
 */
class PdfTextExtractorTest {

    private final PdfTextExtractor extractor = new PdfTextExtractor();

    @Test
    @DisplayName("declares the one format it reads")
    void declaresItsFormat() {
        assertThat(extractor.supportedType()).isEqualTo(FileType.PDF);
    }

    @Test
    @DisplayName("reads the words back out of a generated PDF")
    void readsText() {
        byte[] content = DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines());

        ExtractedText extracted = extractor.extract(content);

        assertThat(extracted.text())
                .contains("PRIYA SHARMA")
                .contains("Spring Boot")
                .contains("Visvesvaraya");
        assertThat(extracted.wordCount()).isGreaterThan(40);
    }

    @Test
    @DisplayName("normalisation is applied on the way out, not left to the caller")
    void returnsCleanedText() {
        byte[] content = DocumentFixtures.pdf(DocumentFixtures.realisticResumeLines());

        ExtractedText extracted = extractor.extract(content);

        // PDFBox pads lines to preserve visual layout. Anything downstream that matched on
        // "Spring Boot" would miss "Spring   Boot", so the collapse happens here, once.
        assertThat(extracted.text()).doesNotContain("  ");
        assertThat(extracted.text()).doesNotContain("\r");
        assertThat(extracted.text().strip()).isEqualTo(extracted.text());
    }

    @Test
    @DisplayName("reports the page count the document actually has")
    void reportsPageCount() {
        byte[] content = DocumentFixtures.pdfWithPages(3, "PRIYA SHARMA", "Java developer");

        assertThat(extractor.extract(content).pageCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("an empty page yields empty text and no complaint")
    void acceptsAPageWithNoText() {
        // This is a scan, from the extractor's side of the problem: valid PDF, zero characters.
        // It is not this class's business to decide whether that is enough text — the service
        // owns the floor, because the answer is the same for a DOCX. Keeping the judgement out
        // of here is what makes that one decision instead of two.
        ExtractedText extracted = extractor.extract(DocumentFixtures.pdfWithNoText());

        assertThat(extracted.text()).isEmpty();
        assertThat(extracted.wordCount()).isZero();
        assertThat(extracted.pageCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("a PDF locked against copying is refused, not quietly read anyway")
    void honoursTheExtractionPermission() {
        byte[] content = DocumentFixtures.pdfWithExtractionForbidden("PRIYA SHARMA", "Java developer");

        // The refusal is ours: PDFBox reports the restriction and leaves the decision to us,
        // and reading the text regardless would have worked perfectly well.
        // catchThrowable rather than catchThrowableOfType because AssertJ swapped that
        // method's argument order after the 3.24.2 that Boot 3.2.5 pins.
        Throwable failure = catchThrowable(() -> extractor.extract(content));

        assertThat(failure)
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("protected against copying");
        assertThat(((UnreadableResumeException) failure).errorCode())
                .isEqualTo(ErrorCode.UNREADABLE_FILE);
    }

    @Test
    @DisplayName("a password-protected PDF is refused with the reason a person can act on")
    void refusesAPasswordProtectedPdf() {
        byte[] content = DocumentFixtures.pdfNeedingAPassword("PRIYA SHARMA");

        assertThatThrownBy(() -> extractor.extract(content))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("needs a password");
    }

    @Test
    @DisplayName("bytes that start like a PDF and then are not do not escape as a 500")
    void refusesCorruptContent() {
        // The signature check passed and the parser still has nothing. Whatever PDFBox throws
        // for this — and it varies — must arrive as our own 422, with the original kept as the
        // cause so the log has the detail and the response does not.
        assertThatThrownBy(() -> extractor.extract(DocumentFixtures.corruptPdf()))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("could not be read")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("empty content is refused rather than parsed")
    void refusesEmptyContent() {
        assertThatThrownBy(() -> extractor.extract(new byte[0]))
                .isInstanceOf(UnreadableResumeException.class);
    }

    @Test
    @DisplayName("the refusal never quotes the file, because the file is somebody's resume")
    void keepsResumeContentOutOfTheMessage() {
        byte[] content = DocumentFixtures.pdfWithExtractionForbidden(
                "PRIYA SHARMA", "priya.sharma@example.test");

        assertThatThrownBy(() -> extractor.extract(content))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageNotContaining("PRIYA")
                .hasMessageNotContaining("example.test");
    }
}

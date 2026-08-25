package com.resumeiq.resume.extract;

import com.resumeiq.support.DocumentFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * DOCX extraction, with one case that justifies the whole design of the class.
 *
 * <p>{@link #readsTableContent()} is why {@code XWPFWordExtractor} is used instead of
 * walking paragraphs. Most resume templates lay out skills and dates in tables, and a
 * paragraph walk returns every word except those — producing a resume with no skills
 * section, which would then be scored and reported as a gap that does not exist.
 */
class DocxTextExtractorTest {

    private final DocxTextExtractor extractor = new DocxTextExtractor();

    @Test
    @DisplayName("declares the one format it reads")
    void declaresItsFormat() {
        assertThat(extractor.supportedType()).isEqualTo(FileType.DOCX);
    }

    @Test
    @DisplayName("reads the paragraphs of a generated DOCX")
    void readsText() {
        byte[] content = DocumentFixtures.docx(DocumentFixtures.realisticResumeLines());

        ExtractedText extracted = extractor.extract(content);

        assertThat(extracted.text())
                .contains("PRIYA SHARMA")
                .contains("Spring Boot")
                .contains("BE Computer Science");
        assertThat(extracted.wordCount()).isGreaterThan(40);
    }

    @Test
    @DisplayName("reads what is inside tables, which is where resumes keep their skills")
    void readsTableContent() {
        byte[] content = DocumentFixtures.docxWithTable(
                new String[] {"PRIYA SHARMA", "Backend developer"},
                new String[][] {
                        {"Languages", "Java, SQL"},
                        {"Frameworks", "Spring Boot, React"},
                        {"Databases", "MySQL, Redis"},
                });

        ExtractedText extracted = extractor.extract(content);

        assertThat(extracted.text())
                .contains("PRIYA SHARMA")
                .contains("Languages")
                .contains("Spring Boot, React")
                .contains("MySQL, Redis");
    }

    @Test
    @DisplayName("no page count when the file does not carry one")
    void reportsNoPageCountForAGeneratedFile() {
        // Word writes a page count when it lays a document out; nothing else does, and POI does
        // not compute one — pagination depends on fonts and page metrics, so counting properly
        // would mean rendering the document. Null is the honest answer, and the response model
        // treats the field as optional for exactly this reason.
        byte[] content = DocumentFixtures.docx("PRIYA SHARMA", "Java developer");

        assertThat(extractor.extract(content).pageCount()).isNull();
    }

    @Test
    @DisplayName("a zip that is not a Word document is refused, not surfaced as a 500")
    void refusesCorruptContent() {
        // Every OOXML file is a zip, so the signature check cannot tell a .docx from a .xlsx or
        // from this. POI signals it with runtime exceptions that are part of its normal
        // contract; catching broadly is what keeps them from becoming a 500.
        assertThatThrownBy(() -> extractor.extract(DocumentFixtures.corruptDocx()))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining("could not be read")
                .hasCauseInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("a renamed legacy .doc is refused with advice about the format")
    void refusesLegacyDocContent() {
        // Only reachable if someone renames a .doc to .docx and the sniffer is bypassed, but the
        // message names that possibility because it is the likeliest explanation.
        assertThatThrownBy(() -> extractor.extract(DocumentFixtures.legacyDoc()))
                .isInstanceOf(UnreadableResumeException.class)
                .hasMessageContaining(".doc");
    }

    @Test
    @DisplayName("empty content is refused rather than parsed")
    void refusesEmptyContent() {
        assertThatThrownBy(() -> extractor.extract(new byte[0]))
                .isInstanceOf(UnreadableResumeException.class);
    }
}

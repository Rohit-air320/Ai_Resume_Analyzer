package com.resumeiq.resume.extract;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

/**
 * DOCX text extraction, via Apache POI.
 *
 * <p>{@link XWPFWordExtractor} is used rather than walking paragraphs by hand, because
 * resumes put a great deal in tables — two-column skill grids, date ranges beside role
 * titles — and a naive paragraph walk misses every word of it. The extractor also picks
 * up headers, footers and text boxes, which is where the contact details usually live.
 *
 * <p>POI's zip-bomb guard is left at its default ratio on purpose. A .docx is a zip
 * archive, so an upload endpoint that reads one is a decompression target; the default
 * refuses anything that expands more than a hundredfold, which no real document does.
 */
@Component
public class DocxTextExtractor implements TextExtractor {

    @Override
    public FileType supportedType() {
        return FileType.DOCX;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(content));
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {

            return ExtractedText.of(extractor.getText(), pageCountOf(document));

        } catch (Exception ex) {
            // Broad for the same reason as the PDF extractor: the input is untrusted and POI
            // signals a wrong or corrupt archive with runtime exceptions
            // (NotOfficeXmlFileException among them) that are part of its normal contract.
            // Naming those classes would tie this code to POI's internals; treating any
            // failure as "unreadable" is both simpler and correct.
            throw new UnreadableResumeException(
                    "This DOCX could not be read — it may be damaged, or saved in the older .doc "
                            + "format with a .docx name. Re-save it from Word or Google Docs and "
                            + "try again.", ex);
        }
    }

    /**
     * Word's own page count, read from the document properties.
     *
     * <p>This is a cached number that Word wrote the last time it laid the document out,
     * not something POI computes — pagination depends on fonts, page size and printer
     * metrics, so counting pages properly would mean rendering the document. Files
     * produced by tools other than Word often omit it, hence the null.
     */
    private static Integer pageCountOf(XWPFDocument document) {
        if (document.getProperties() == null || document.getProperties().getExtendedProperties() == null) {
            return null;
        }
        int pages = document.getProperties().getExtendedProperties().getPages();
        return pages > 0 ? pages : null;
    }
}

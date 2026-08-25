package com.resumeiq.resume.extract;

import com.resumeiq.common.exception.ApiException;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

/**
 * PDF text extraction, via Apache PDFBox.
 *
 * <p>Two decisions here are worth explaining.
 *
 * <p><strong>Reading order is sorted by position.</strong> A PDF stores text in the order
 * the generator happened to write it, which for the two-column resume layouts that every
 * template site sells is not the order a person reads. Left to itself the extractor
 * interleaves the sidebar with the body, and the result reads like two documents shuffled
 * together — which then gets scored. Sorting by position costs a little time and produces
 * text a human would recognise.
 *
 * <p><strong>The document's own extraction permission is honoured.</strong> A PDF can be
 * signed and locked against copying, and PDFBox will tell us so rather than enforce it.
 * Reading it anyway would work. It is refused instead, and the refusal explains what to
 * upload instead.
 */
@Component
public class PdfTextExtractor implements TextExtractor {

    @Override
    public FileType supportedType() {
        return FileType.PDF;
    }

    @Override
    public ExtractedText extract(byte[] content) {
        try (PDDocument document = Loader.loadPDF(content)) {
            AccessPermission permission = document.getCurrentAccessPermission();
            if (permission != null && !permission.canExtractContent()) {
                throw new UnreadableResumeException(
                        "This PDF is protected against copying text, so its content cannot be read. "
                                + "Upload a version without that restriction, or export it again from "
                                + "your editor.");
            }

            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            stripper.setLineSeparator("\n");
            return ExtractedText.of(stripper.getText(document), document.getNumberOfPages());

        } catch (InvalidPasswordException ex) {
            throw new UnreadableResumeException(
                    "This PDF needs a password to open. Upload an unprotected copy.", ex);
        } catch (ApiException rethrow) {
            // Our own refusal above, already worded for the user. Must be re-thrown before
            // the catch-all below turns it into a different message.
            throw rethrow;
        } catch (Exception ex) {
            // Deliberately broad. This is a third-party parser running on a file a stranger
            // chose, and a malformed PDF can surface as almost anything — IOException,
            // IllegalArgumentException, an index out of bounds deep inside a font table. Any
            // of them mean the same thing to the person waiting: we could not read this file.
            // Letting one escape would produce a 500 and log a stack trace containing the
            // document's bytes.
            throw new UnreadableResumeException(
                    "This PDF could not be read — it may be damaged or incomplete. "
                            + "Try exporting it again, or upload a DOCX instead.", ex);
        }
    }
}

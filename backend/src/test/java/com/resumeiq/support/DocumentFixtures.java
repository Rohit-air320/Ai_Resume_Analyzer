package com.resumeiq.support;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Real PDFs and real DOCX files, built in memory.
 *
 * <p>Checked-in binary fixtures were the obvious alternative and are worse in every way that
 * matters here. Nobody can review one, nobody can tell from the filename what is inside, and a
 * test that fails because the fixture is subtly wrong is a bad afternoon. These are generated
 * by the same libraries that will read them, so what each test is asserting about is visible in
 * the test.
 *
 * <p>The one thing this cannot produce is a scanned resume — a page containing an image of text
 * and no text at all. {@link #pdfWithNoText()} stands in for it, because from the extractor's
 * side of the problem a scan and an empty page are the same thing: a document that parses
 * perfectly and yields nothing.
 */
public final class DocumentFixtures {

    /**
     * Owner password for the restricted PDFs below. Spelled with "example" because the
     * repository's secret scanner is right to flag any test credential that is not obviously
     * fake, and a test is not a good reason to make it start ignoring things.
     */
    private static final String EXAMPLE_OWNER_PASSWORD = "example-owner-key";

    /** User password, i.e. the one needed to open the document at all. */
    private static final String EXAMPLE_OPEN_PASSWORD = "example-open-key";

    private static final float MARGIN = 56f;
    private static final float TOP = 760f;
    private static final float LINE_HEIGHT = 15f;
    private static final float FONT_SIZE = 11f;

    private DocumentFixtures() {
    }

    /** A one-page PDF containing the given lines of text. */
    public static byte[] pdf(String... lines) {
        try (PDDocument document = new PDDocument()) {
            writePage(document, lines);
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** A PDF of {@code pages} pages, so the reported page count can be asserted. */
    public static byte[] pdfWithPages(int pages, String... lines) {
        try (PDDocument document = new PDDocument()) {
            for (int page = 1; page <= pages; page++) {
                writePage(document, lines);
            }
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** A valid, parseable PDF with a blank page — the extractor's view of a scan. */
    public static byte[] pdfWithNoText() {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage(PDRectangle.A4));
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * A PDF that opens without a password but forbids copying its text — the state a signed
     * or rights-managed document arrives in.
     */
    public static byte[] pdfWithExtractionForbidden(String... lines) {
        try (PDDocument document = new PDDocument()) {
            writePage(document, lines);
            AccessPermission permission = new AccessPermission();
            permission.setCanExtractContent(false);
            permission.setCanExtractForAccessibility(false);
            StandardProtectionPolicy policy =
                    new StandardProtectionPolicy(EXAMPLE_OWNER_PASSWORD, "", permission);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** A PDF that cannot be opened at all without the right password. */
    public static byte[] pdfNeedingAPassword(String... lines) {
        try (PDDocument document = new PDDocument()) {
            writePage(document, lines);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(
                    EXAMPLE_OWNER_PASSWORD, EXAMPLE_OPEN_PASSWORD, new AccessPermission());
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /** A DOCX whose body is the given paragraphs. */
    public static byte[] docx(String... paragraphs) {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * A DOCX with a two-column table, which is how most resume templates lay out skills.
     * A paragraph-only walk of the document would miss every word of it.
     */
    public static byte[] docxWithTable(String[] paragraphs, String[][] rows) {
        try (XWPFDocument document = new XWPFDocument()) {
            for (String paragraph : paragraphs) {
                document.createParagraph().createRun().setText(paragraph);
            }
            XWPFTable table = document.createTable(rows.length, rows[0].length);
            for (int row = 0; row < rows.length; row++) {
                for (int column = 0; column < rows[row].length; column++) {
                    table.getRow(row).getCell(column).setText(rows[row][column]);
                }
            }
            return bytesOf(document);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Bytes that begin with a correct PDF header and then lie. Every signature check passes
     * and the parser still has nothing to work with, which is the case a magic-byte check
     * alone cannot catch.
     */
    public static byte[] corruptPdf() {
        return "%PDF-1.7\nthis is not a cross-reference table".getBytes(StandardCharsets.UTF_8);
    }

    /** A zip file, which is what a DOCX is, containing nothing a Word document would contain. */
    public static byte[] corruptDocx() {
        byte[] header = {0x50, 0x4B, 0x03, 0x04};
        byte[] rest = "not an OOXML package".getBytes(StandardCharsets.UTF_8);
        byte[] combined = new byte[header.length + rest.length];
        System.arraycopy(header, 0, combined, 0, header.length);
        System.arraycopy(rest, 0, combined, header.length, rest.length);
        return combined;
    }

    /** The OLE2 header of a pre-2007 .doc, for the "save it as .docx" path. */
    public static byte[] legacyDoc() {
        return new byte[] {
                (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
                (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1,
                0x00, 0x00, 0x00, 0x00,
        };
    }

    /** A plain text file, which is neither of the accepted formats. */
    public static byte[] plainText() {
        return "Priya Sharma\nJava developer\n".getBytes(StandardCharsets.UTF_8);
    }

    /** Enough prose to clear the minimum-characters floor without saying anything specific. */
    public static String[] realisticResumeLines() {
        return new String[] {
                "PRIYA SHARMA",
                "Backend developer - Bengaluru - priya.sharma@example.test",
                "",
                "EXPERIENCE",
                "Software Engineer, Northwind Retail, 2023 to present.",
                "Built REST services in Java 17 and Spring Boot serving 40,000 daily requests.",
                "Moved the order pipeline onto MySQL, cutting median query time from 800ms to 90ms.",
                "Wrote the integration test suite the team now gates every release on.",
                "",
                "Junior Developer, Halcyon Software, 2022 to 2023.",
                "Maintained a React dashboard used by the operations team every morning.",
                "Added pagination and caching that removed a recurring timeout on the reports page.",
                "",
                "SKILLS",
                "Java, Spring Boot, MySQL, React, Git, Docker, REST APIs, JUnit, Maven.",
                "",
                "EDUCATION",
                "BE Computer Science, Visvesvaraya Technological University, 2022.",
        };
    }

    private static void writePage(PDDocument document, String... lines) throws IOException {
        PDPage page = new PDPage(PDRectangle.A4);
        document.addPage(page);
        try (PDPageContentStream content = new PDPageContentStream(document, page)) {
            content.beginText();
            content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), FONT_SIZE);
            content.newLineAtOffset(MARGIN, TOP);
            for (String line : lines) {
                // showText refuses a newline, so each line is drawn and the cursor moved by hand.
                content.showText(line);
                content.newLineAtOffset(0, -LINE_HEIGHT);
            }
            content.endText();
        }
    }

    private static byte[] bytesOf(PDDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.save(out);
        return out.toByteArray();
    }

    private static byte[] bytesOf(XWPFDocument document) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        document.write(out);
        return out.toByteArray();
    }
}

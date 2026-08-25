package com.resumeiq.resume.extract;

import java.util.Optional;

/**
 * The document formats this application will read, identified by what a file
 * <em>is</em> rather than by what the request claims it is.
 *
 * <p>A multipart upload arrives with two pieces of metadata the uploader controls
 * completely: the filename and the {@code Content-Type} header. Trusting either is how
 * a {@code .pdf} that is really an executable, or a {@code text/plain} that is really a
 * zip bomb, gets past a validator. So the decision is made from the leading bytes of
 * the content, which the uploader would have to actually forge a valid file to fake —
 * and a file that genuinely starts with {@code %PDF-} is a PDF.
 *
 * <p>The signature check is necessary but not sufficient, and deliberately so. Every
 * OOXML document is a zip, so a spreadsheet and a {@code .docx} share the same first
 * four bytes; the parser is what finally decides, and its failure is reported as an
 * unreadable file. The point of this class is to reject the obviously wrong cheaply,
 * before any bytes reach a third-party parser.
 */
public enum FileType {

    /** {@code %PDF-} — the header the PDF specification requires at byte zero. */
    PDF("application/pdf", "pdf", new byte[] {0x25, 0x50, 0x44, 0x46, 0x2D}),

    /**
     * {@code PK\003\004} — the local file header of a zip archive, which is what every
     * OOXML document is underneath. Shared with .xlsx, .pptx and plain .zip, so this
     * narrows the field rather than settling it.
     */
    DOCX("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "docx", new byte[] {0x50, 0x4B, 0x03, 0x04});

    /**
     * The OLE2 compound-document header, which is what a pre-2007 {@code .doc} starts
     * with. Not a supported type — it is recognised only so the refusal can name the
     * problem and the fix ("save it as .docx") instead of shrugging at the user.
     */
    private static final byte[] LEGACY_DOC_SIGNATURE = {
            (byte) 0xD0, (byte) 0xCF, (byte) 0x11, (byte) 0xE0,
            (byte) 0xA1, (byte) 0xB1, (byte) 0x1A, (byte) 0xE1,
    };

    private final String contentType;
    private final String extension;
    private final byte[] signature;

    FileType(String contentType, String extension, byte[] signature) {
        this.contentType = contentType;
        this.extension = extension;
        this.signature = signature;
    }

    /** The canonical media type, stored on the resume row rather than the one sent to us. */
    public String contentType() {
        return contentType;
    }

    /** Lower-case extension, without the dot, used when naming the stored file. */
    public String extension() {
        return extension;
    }

    /** Human label for messages and for the UI. */
    public String label() {
        return name();
    }

    /**
     * Identifies the content by its leading bytes.
     *
     * @return the matching type, or empty if nothing recognised it
     */
    public static Optional<FileType> sniff(byte[] content) {
        for (FileType candidate : values()) {
            if (startsWith(content, candidate.signature)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    /** True for a pre-2007 Word document, which is refused with specific advice. */
    public static boolean isLegacyWordDocument(byte[] content) {
        return startsWith(content, LEGACY_DOC_SIGNATURE);
    }

    /** Comma-separated list of what is accepted, for the {@code accept} attribute and error copy. */
    public static String supportedExtensions() {
        return "PDF, DOCX";
    }

    private static boolean startsWith(byte[] content, byte[] signature) {
        if (content == null || content.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }
}

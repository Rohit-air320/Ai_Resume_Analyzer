package com.resumeiq.resume.extract;

/**
 * Turns one document format into text.
 *
 * <p>One implementation per format, each a Spring bean, collected into a map by
 * {@link TextExtractionService}. Supporting RTF or plain text later is a new class and
 * nothing else — no switch statement anywhere grows a case, which is the whole reason
 * this is an interface rather than two static methods.
 *
 * <p>Implementations receive the file as a byte array rather than a stream, because both
 * underlying parsers need random access and would buffer it themselves anyway. The size
 * cap is what makes that safe.
 */
public interface TextExtractor {

    /** The format this extractor handles. */
    FileType supportedType();

    /**
     * Reads the document.
     *
     * @param content the whole file
     * @return normalised text plus a page count where the format reports one
     * @throws UnreadableResumeException if the bytes cannot be parsed, the document
     *                                  refuses extraction, or it needs a password
     */
    ExtractedText extract(byte[] content);
}

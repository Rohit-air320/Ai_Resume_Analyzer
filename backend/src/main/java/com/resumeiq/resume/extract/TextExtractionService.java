package com.resumeiq.resume.extract;

import com.resumeiq.config.ResumeIqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Picks the right extractor, then enforces the limits that apply whatever the format.
 *
 * <p>The extractors are injected as a list and indexed by the format each one declares,
 * so this class contains no knowledge of PDF or DOCX at all. Adding a format is adding a
 * bean.
 *
 * <p>Two checks live here rather than in the extractors, because they are the same
 * judgement in both cases. A floor: text shorter than the configured minimum means the
 * file parsed and said nothing, which in practice is a scan — a photograph of a resume,
 * perfectly valid as a PDF and worthless as input. Rejecting it with an explanation is
 * the only honest answer, since scoring an empty document would produce a confident
 * number about nothing. And a ceiling, applied by truncation rather than refusal: a
 * 200-page PDF is somebody's mistake, not an attack, and taking the first 40,000
 * characters is a better response than a rejection they cannot act on.
 */
@Service
public class TextExtractionService {

    private static final Logger log = LoggerFactory.getLogger(TextExtractionService.class);

    private final Map<FileType, TextExtractor> extractors = new EnumMap<>(FileType.class);
    private final ResumeIqProperties.Upload limits;

    public TextExtractionService(List<TextExtractor> extractors, ResumeIqProperties properties) {
        this.limits = properties.upload();
        for (TextExtractor extractor : extractors) {
            TextExtractor previous = this.extractors.put(extractor.supportedType(), extractor);
            if (previous != null) {
                // Two beans claiming one format would make behaviour depend on bean ordering,
                // which is the kind of bug that reproduces on one machine and not another.
                throw new IllegalStateException("Two extractors registered for " + extractor.supportedType()
                        + ": " + previous.getClass().getSimpleName() + " and "
                        + extractor.getClass().getSimpleName());
            }
        }
    }

    /**
     * Extracts, cleans, measures and bounds the text of one document.
     *
     * @throws UnreadableResumeException if the file cannot be parsed or holds too little text
     */
    public ExtractedText extract(FileType type, byte[] content) {
        TextExtractor extractor = extractors.get(type);
        if (extractor == null) {
            // Unreachable while FileType and the bean set agree, which the startup check above
            // and a test both enforce. Kept because "unreachable" and "absent" are different.
            throw new UnreadableResumeException(
                    "No reader is available for " + type.label() + " files.");
        }

        ExtractedText extracted = extractor.extract(content);

        if (!extracted.hasAtLeast(limits.minExtractedCharacters())) {
            // Counts only. The one thing that must never be logged here is the text itself.
            log.info("Extraction produced too little text: {} characters from a {} file",
                    extracted.text().length(), type.label());
            throw new UnreadableResumeException(
                    "Almost no text could be read from this file. If it is a scan or a photo of a "
                            + "resume, the words are part of the image — export a text version from "
                            + "the program you wrote it in and upload that.");
        }

        ExtractedText bounded = extracted.truncatedTo(limits.maxExtractedCharacters());
        if (bounded.text().length() < extracted.text().length()) {
            log.info("Resume text truncated from {} to {} characters",
                    extracted.text().length(), bounded.text().length());
        }
        return bounded;
    }
}

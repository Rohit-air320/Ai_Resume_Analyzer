package com.resumeiq.analysis.ai;

/**
 * What a provider gave back.
 *
 * @param text  the raw response body text, exactly as returned. Parsing belongs to
 *              {@link AiAdviceReader}, not here — a transport type that also parses is a type that
 *              cannot be tested against a real response without also testing the parser.
 * @param model the model that answered, which is not always the model that was asked for: providers
 *              alias and upgrade names. Stored on the analysis so a result can be attributed to the
 *              thing that actually produced it.
 */
public record AiCompletion(String text, String model) {

    /** True when the provider returned nothing usable. Treated as a failure, not as empty advice. */
    public boolean isEmpty() {
        return text == null || text.isBlank();
    }
}

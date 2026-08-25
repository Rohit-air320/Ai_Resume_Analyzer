package com.resumeiq.analysis.ai;

/**
 * Sends a prompt somewhere and returns text.
 *
 * <p>Deliberately the narrowest interface that can express the job. Everything above it — building the
 * prompt, parsing the response, sanitising the advice, scoring — is provider-independent, so swapping
 * Anthropic for another vendor is one class, and testing the layers above it needs no network and no
 * key. That is why the tests in this project can cover the parser and the sanitiser against
 * deliberately malformed responses, which is the code most likely to break in production and the
 * hardest to exercise against a live model.
 *
 * <p>Implementations must throw {@link AiUnavailableException} for anything transport-shaped and
 * {@link AiInvalidResponseException} for a response that arrived but made no sense. They must not
 * return null and must not log the prompt.
 */
public interface AiProvider {

    /**
     * Sends one prompt.
     *
     * @throws AiUnavailableException      on timeout, connection failure, rate limit or a provider
     *                                     error status
     * @throws AiInvalidResponseException when the response body is not the shape the provider
     *                                     documents
     */
    AiCompletion complete(AiPrompt prompt);

    /** Name for logs and for the analysis record. Never includes a key or a URL with credentials. */
    String name();
}

package com.resumeiq.analysis.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.config.ResumeIqProperties;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to Anthropic's Messages API.
 *
 * <p>The only class in the project that makes a network call, and the only one that knows a vendor's
 * request shape. Everything above it works against {@link AiProvider}, so supporting a second vendor is
 * a second class of about this size and no changes anywhere else.
 *
 * <h2>The key</h2>
 *
 * <p>Read from configuration, which reads it from the environment. It is never logged, never included in
 * an exception message, never returned by an endpoint, and never sent anywhere except this one request
 * header. The frontend has no path to it at all: the browser calls this backend, this backend calls the
 * provider. That is the whole reason the AI call lives on the server.
 *
 * <h2>What is sent</h2>
 *
 * <p>The prompt, which contains the resume text and the posting. Nothing else — no user id, no email, no
 * account details, no history. A provider processing a resume is unavoidable for a tool that analyses
 * resumes; a provider learning whose resume it is, is avoidable, so it is avoided.
 */
public class AnthropicProvider implements AiProvider {

    /** The API version this provider is written against. Anthropic requires it on every request. */
    private static final String API_VERSION = "2023-06-01";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final RestClient client;
    private final String model;
    private final int maxOutputTokens;

    /**
     * @param client          pre-configured with the base URL and the timeouts. Built in
     *                        {@link AiConfiguration} rather than here so this class stays testable
     *                        against a stub server and holds no configuration logic.
     * @param settings        the AI settings, for the model name and the output ceiling
     */
    public AnthropicProvider(RestClient client, ResumeIqProperties.Ai settings) {
        this.client = client;
        this.model = settings.model();
        this.maxOutputTokens = settings.maxOutputTokens();
    }

    @Override
    public AiCompletion complete(AiPrompt prompt) {
        String body;
        try {
            body = client.post()
                    .uri("/v1/messages")
                    .header("anthropic-version", API_VERSION)
                    .body(request(prompt))
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException failure) {
            // The provider answered with an error status. Its body can echo parts of the request, so
            // it is never included in the message that leaves this method — the cause carries it to
            // the log, and the log is ours.
            throw new AiUnavailableException(
                    "The AI provider returned status " + failure.getStatusCode().value() + ".",
                    failure);
        } catch (RestClientException failure) {
            // Timeout, DNS, connection refused, TLS. All the same to a caller.
            throw new AiUnavailableException(
                    "The AI provider could not be reached. Your scores are unaffected.", failure);
        }
        return read(body);
    }

    @Override
    public String name() {
        return "anthropic:" + model;
    }

    /**
     * Builds the request body.
     *
     * <p>The system prompt goes in the top-level {@code system} field rather than as a message, which is
     * what the API expects and is also the boundary that matters here: the rules are system, the
     * documents are user content.
     */
    private Map<String, Object> request(AiPrompt prompt) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", prompt.user());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("max_tokens", maxOutputTokens);
        body.put("system", prompt.system());
        body.put("messages", List.of(message));
        // Zero temperature, because the same resume analysed twice should not produce different advice.
        // The scores are already deterministic; leaving the words to drift would make the two halves of
        // one result disagree about how stable the product is.
        body.put("temperature", 0);
        return body;
    }

    /**
     * Pulls the text out of the response envelope.
     *
     * <p>Concatenates every text block rather than taking the first. A single block is what normally
     * comes back, but the response is documented as a list and a JSON object split across two blocks
     * would parse as truncated if only the first were read.
     */
    private AiCompletion read(String body) {
        if (body == null || body.isBlank()) {
            throw new AiInvalidResponseException("The AI provider returned an empty body.");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (JsonProcessingException cause) {
            throw new AiInvalidResponseException(
                    "The AI provider's response envelope was not valid JSON.", cause);
        }
        JsonNode content = root.path("content");
        if (!content.isArray()) {
            // Includes the documented error envelope, which has "error" where "content" belongs.
            String type = root.path("error").path("type").asText("");
            throw new AiInvalidResponseException(type.isBlank()
                    ? "The AI provider's response had no content array."
                    : "The AI provider reported an error of type " + type + ".");
        }
        StringBuilder text = new StringBuilder();
        for (JsonNode block : content) {
            if ("text".equals(block.path("type").asText()) && block.path("text").isTextual()) {
                text.append(block.path("text").asText());
            }
        }
        String answered = root.path("model").asText(model);
        return new AiCompletion(text.toString(), answered);
    }
}

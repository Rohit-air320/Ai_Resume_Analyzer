package com.resumeiq.analysis.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.resumeiq.analysis.ResumeSection;
import com.resumeiq.analysis.engine.ScoreCard;
import com.resumeiq.recommendation.Priority;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a model's reply into {@link AiAdvice}.
 *
 * <p>Written on the assumption that the response will be wrong in small ways, because it will be. A
 * model asked for bare JSON returns it most of the time and the rest of the time returns the same JSON
 * inside a markdown fence, or with "Here is the analysis:" in front of it, or with a cheerful sentence
 * after the closing brace, or truncated mid-object because the output limit was reached. The first three
 * are recoverable and are recovered here. Only the fourth is a real failure.
 *
 * <h2>Every field is optional</h2>
 *
 * <p>A missing key yields an empty list, not an exception. This matters more than it looks: the
 * alternative is that one absent field discards a response that was otherwise five good suggestions,
 * and the user sees the offline advice instead of most of what the model wrote. Only a response that
 * cannot be parsed as an object at all is rejected.
 *
 * <h2>Nothing here trusts a value</h2>
 *
 * <p>Enums are matched case-insensitively and fall back to a default rather than throwing. Scores are
 * clamped. Strings are trimmed. Unknown section names are dropped. The reader's job is to get whatever
 * is usable out of the response; deciding whether the content is <em>allowed</em> is
 * {@link AdviceSanitiser}'s job, and keeping those two apart is what makes both testable.
 */
public final class AiAdviceReader {

    /** Shared and thread-safe once configured, which is how Jackson is meant to be used. */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private AiAdviceReader() {
    }

    /**
     * Reads a completion.
     *
     * @param completion what the provider returned
     * @return the advice, with {@code source} set to the model that produced it
     * @throws AiInvalidResponseException when no JSON object can be recovered from the response
     */
    public static AiAdvice read(AiCompletion completion) {
        if (completion == null || completion.isEmpty()) {
            throw new AiInvalidResponseException("The AI provider returned an empty response.");
        }
        JsonNode root = parse(completion.text());
        String source = completion.model() == null || completion.model().isBlank()
                ? "ai" : completion.model();

        return new AiAdvice(
                text(root, "overallFeedback"),
                improvements(root.path("improvements")),
                gaps(root.path("skillGaps")),
                projects(root.path("recommendedProjects")),
                learning(root.path("learningRecommendations")),
                keywords(root.path("suggestedKeywords")),
                sectionNotes(root.path("sectionScores")),
                scores(root),
                source);
    }

    /**
     * Recovers a JSON object from whatever the model actually sent.
     *
     * <p>Takes the span from the first brace to the last, which handles a fence, a preamble and a
     * postscript in one step and without a regex, and then lets Jackson read the first complete value
     * out of that span. Jackson ignores trailing tokens by default, and that leniency is kept
     * deliberately rather than switched off: it is what makes a postscript containing its own braces
     * survivable — {@code {...} Hope this helps! {see the summary}} widens the span past the end of the
     * object, and strict parsing would reject an otherwise perfectly good response over a closing
     * pleasantry. The same rule means a model that answered twice yields its first answer, which is an
     * arbitrary choice but a harmless one: every structured claim in it still has to survive
     * {@link AdviceSanitiser}, and a response that survives nothing is replaced wholesale.
     *
     * <p>What is genuinely unrecoverable is a response truncated mid-object, because the output limit
     * was reached. There is no closing brace to span to, so it is rejected here.
     */
    private static JsonNode parse(String raw) {
        int open = raw.indexOf('{');
        int close = raw.lastIndexOf('}');
        if (open < 0 || close <= open) {
            throw new AiInvalidResponseException(
                    "The AI provider did not return a JSON object. Falling back to computed advice.");
        }
        try {
            JsonNode root = MAPPER.readTree(raw.substring(open, close + 1));
            if (!root.isObject()) {
                throw new AiInvalidResponseException(
                        "The AI response parsed to a " + root.getNodeType() + " rather than an object.");
            }
            return root;
        } catch (JsonProcessingException cause) {
            // The cause reaches the log; its message can quote the malformed span, and that span is
            // part of a response about somebody's resume, so it never reaches the client.
            throw new AiInvalidResponseException(
                    "The AI response was not valid JSON. Falling back to computed advice.", cause);
        }
    }

    private static List<AiAdvice.Improvement> improvements(JsonNode array) {
        List<AiAdvice.Improvement> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            String title = text(node, "title");
            if (title.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.Improvement(title, text(node, "detail"),
                    priority(node), section(node)));
        }
        return items;
    }

    private static List<AiAdvice.GapNote> gaps(JsonNode array) {
        List<AiAdvice.GapNote> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            // "skill" is what the prompt asks for; "slug" is what a model writes when it has read the
            // findings closely. Accepting both costs one line and saves a whole discarded list.
            String slug = firstOf(node, "skill", "slug");
            if (slug.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.GapNote(slug, text(node, "detail"), priority(node)));
        }
        return items;
    }

    private static List<AiAdvice.ProjectIdea> projects(JsonNode array) {
        List<AiAdvice.ProjectIdea> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            String title = text(node, "title");
            if (title.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.ProjectIdea(title, text(node, "detail"),
                    strings(node.path("skills"))));
        }
        return items;
    }

    private static List<AiAdvice.LearningTopic> learning(JsonNode array) {
        List<AiAdvice.LearningTopic> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            String title = text(node, "title");
            if (title.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.LearningTopic(title, text(node, "detail"),
                    firstOf(node, "url", "resourceUrl"), priority(node)));
        }
        return items;
    }

    private static List<AiAdvice.KeywordPlacement> keywords(JsonNode array) {
        List<AiAdvice.KeywordPlacement> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            // A bare string here is the most common schema slip, and it is the one case that must not
            // be salvaged: a term with no placement is exactly the keyword-stuffing suggestion the
            // rules forbid, so it is dropped rather than kept with an empty placement.
            if (node.isTextual()) {
                continue;
            }
            String term = text(node, "term");
            String placement = firstOf(node, "placement", "where");
            if (term.isBlank() || placement.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.KeywordPlacement(term, placement));
        }
        return items;
    }

    private static List<AiAdvice.SectionNote> sectionNotes(JsonNode array) {
        List<AiAdvice.SectionNote> items = new ArrayList<>();
        for (JsonNode node : each(array)) {
            ResumeSection section = section(node);
            String note = text(node, "note");
            if (section == null || note.isBlank()) {
                continue;
            }
            items.add(new AiAdvice.SectionNote(section, note));
        }
        return items;
    }

    /**
     * The model's own scores, clamped.
     *
     * <p>Collected only so the cross-check in {@link AiAdviceSource} can log a disagreement. Nothing
     * downstream reports these, and a wide gap between them and the computed scores is a signal that
     * the prompt or the engine deserves a look — not a reason to change a number the user sees.
     */
    private static Map<String, Integer> scores(JsonNode root) {
        Map<String, Integer> found = new LinkedHashMap<>();
        for (String name : ScoreCard.scoreNames()) {
            JsonNode node = root.path(name);
            if (node.isInt() || node.isLong() || node.isDouble()) {
                found.put(name, ScoreCard.clamp(node.asInt()));
            }
        }
        return found;
    }

    /** An array's elements, or nothing. A key that came back as an object or a string yields nothing. */
    private static Iterable<JsonNode> each(JsonNode node) {
        return node != null && node.isArray() ? node : List.of();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().strip() : "";
    }

    /** The first of several accepted spellings that is present. */
    private static String firstOf(JsonNode node, String... fields) {
        for (String field : fields) {
            String value = text(node, field);
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : each(array)) {
            if (node.isTextual() && !node.asText().isBlank()) {
                values.add(node.asText().strip());
            }
        }
        return values;
    }

    /**
     * Priority, defaulting to {@code MEDIUM}.
     *
     * <p>A default rather than a rejection, because an unparseable priority is a badge colour and
     * discarding an otherwise good suggestion over it would be the wrong trade.
     */
    private static Priority priority(JsonNode node) {
        String raw = text(node, "priority").toUpperCase(Locale.ROOT);
        return switch (raw) {
            case "HIGH", "CRITICAL", "URGENT" -> Priority.HIGH;
            case "LOW", "MINOR", "OPTIONAL" -> Priority.LOW;
            default -> Priority.MEDIUM;
        };
    }

    /** The section, or null when the model named one that is not in the enum. */
    private static ResumeSection section(JsonNode node) {
        String raw = firstOf(node, "section", "name").toUpperCase(Locale.ROOT).replace(' ', '_');
        if (raw.isBlank()) {
            return null;
        }
        for (ResumeSection candidate : ResumeSection.values()) {
            if (candidate.name().equals(raw)) {
                return candidate;
            }
        }
        return null;
    }
}

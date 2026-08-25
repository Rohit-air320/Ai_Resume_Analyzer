package com.resumeiq.analysis.ai;

import com.resumeiq.analysis.engine.AnalysisFacts;
import com.resumeiq.analysis.engine.ScoreCard;
import com.resumeiq.config.ResumeIqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Asks a language model for the words, and never lets that decide whether the analysis succeeds.
 *
 * <p>The whole class is arranged around one rule: a provider problem must not cost the user their
 * result. The scores were computed before this ran, the offline writer can produce every list this
 * produces, so any failure here — timeout, rate limit, malformed JSON, empty response, or an exception
 * nobody planned for — ends with complete advice from {@link OfflineAdviceSource} rather than an error
 * page. The user is told which writer produced their advice; they are not told to try again later.
 *
 * <h2>Partial responses are topped up, not discarded</h2>
 *
 * <p>A model that writes four good improvements and omits the learning list is the common partial
 * failure. Throwing that away for the sake of a uniform code path would lose the four good ones, so the
 * missing lists are filled from the offline writer and the good ones are kept.
 *
 * <h2>The score cross-check</h2>
 *
 * <p>The model is asked for the six scores it was given, and its answers are compared to the computed
 * ones. Nothing downstream uses them. It is a monitoring signal: if a model that has been handed the
 * findings and the arithmetic still puts the overall score twenty points away, that is worth knowing
 * about — the prompt may be unclear or the engine may be measuring something a reader would not
 * recognise. Logged, never acted on, which is the only safe thing to do with a second opinion nobody
 * asked the user about.
 */
public class AiAdviceSource implements AdviceSource {

    private static final Logger log = LoggerFactory.getLogger(AiAdviceSource.class);

    private final AiProvider provider;
    private final OfflineAdviceSource offline;
    private final ResumeIqProperties.Ai settings;

    public AiAdviceSource(AiProvider provider, OfflineAdviceSource offline,
                          ResumeIqProperties.Ai settings) {
        this.provider = provider;
        this.offline = offline;
        this.settings = settings;
    }

    @Override
    public AiAdvice adviseOn(AnalysisFacts facts, String postingText) {
        AiPrompt prompt = AnalysisPrompts.build(facts, postingText, settings.maxPromptCharacters());
        int attempts = settings.maxRetries() + 1;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                AiCompletion completion = provider.complete(prompt);
                AiAdvice advice = AdviceSanitiser.clean(AiAdviceReader.read(completion), facts);
                if (advice.isEmpty()) {
                    // Parsed, sanitised, and nothing survived — a response that was entirely about
                    // skills the posting never mentioned. Treated as a failed attempt, because it is.
                    throw new AiInvalidResponseException(
                            "Nothing in the AI response survived validation against the findings.");
                }
                crossCheck(advice.modelScores(), facts.scores());
                log.debug("AI advice accepted from {} on attempt {} of {}: {} items",
                        provider.name(), attempt, attempts, advice.itemCount());
                return topUp(advice, facts, postingText);
            } catch (AiUnavailableException | AiInvalidResponseException failure) {
                boolean last = attempt == attempts;
                // The message, not the stack trace, and never the prompt: the prompt contains a
                // resume. On the final attempt the stack trace is worth having, so it is logged then.
                if (last) {
                    log.warn("AI advice unavailable from {} after {} attempt(s); using the offline "
                            + "writer. Scores are unaffected.", provider.name(), attempts, failure);
                } else {
                    log.info("AI attempt {} of {} failed ({}); retrying.", attempt, attempts,
                            failure.getMessage());
                }
            } catch (RuntimeException unexpected) {
                // A provider is asked for exactly two exception types, so a third one is a bug —
                // in an HTTP client, a JSON library, or this package. A bug is still not a reason to
                // throw away a result that has already been computed, so it falls back like any
                // other failure. Logged at warn on every attempt rather than only the last, because
                // unlike a timeout this is nobody's expected failure and should not be quiet.
                log.warn("AI attempt {} of {} from {} failed unexpectedly; falling back to the "
                                + "offline writer if this persists. Scores are unaffected.",
                        attempt, attempts, provider.name(), unexpected);
            }
        }
        return fallback(facts, postingText);
    }

    @Override
    public String describe() {
        return provider.name();
    }

    /**
     * Fills any list the model left empty from the offline writer.
     *
     * <p>Section notes are the most common omission and the most visible one — an empty note beside
     * every section score looks like a bug rather than a model being terse.
     */
    private AiAdvice topUp(AiAdvice advice, AnalysisFacts facts, String postingText) {
        if (!advice.improvements().isEmpty() && !advice.skillGaps().isEmpty()
                && !advice.sectionNotes().isEmpty() && !advice.overallFeedback().isBlank()) {
            return advice;
        }
        AiAdvice computed = offline.adviseOn(facts, postingText);
        return new AiAdvice(
                advice.overallFeedback().isBlank() ? computed.overallFeedback()
                        : advice.overallFeedback(),
                pick(advice.improvements(), computed.improvements()),
                pick(advice.skillGaps(), computed.skillGaps()),
                pick(advice.recommendedProjects(), computed.recommendedProjects()),
                pick(advice.learningRecommendations(), computed.learningRecommendations()),
                pick(advice.suggestedKeywords(), computed.suggestedKeywords()),
                pick(advice.sectionNotes(), computed.sectionNotes()),
                advice.modelScores(),
                advice.source() + " (some sections written from the computed findings)");
    }

    /** The model's list when it wrote one, the computed list when it did not. */
    private static <T> List<T> pick(List<T> fromModel, List<T> computed) {
        return fromModel.isEmpty() ? computed : fromModel;
    }

    private AiAdvice fallback(AnalysisFacts facts, String postingText) {
        AiAdvice computed = offline.adviseOn(facts, postingText);
        return computed.from(computed.source() + ", after " + provider.name() + " was unavailable");
    }

    /**
     * Logs any score the model disagrees with by more than the configured tolerance.
     *
     * <p>Deliberately one log line per analysis rather than per score, and at {@code info} rather than
     * {@code warn}: a disagreement is interesting, not wrong. Nothing here changes a number.
     */
    private void crossCheck(Map<String, Integer> modelScores, ScoreCard computed) {
        if (modelScores.isEmpty()) {
            return;
        }
        StringBuilder drift = new StringBuilder();
        for (String name : ScoreCard.scoreNames()) {
            Integer claimed = modelScores.get(name);
            if (claimed == null) {
                continue;
            }
            int gap = Math.abs(claimed - computed.byName(name));
            if (gap > settings.scoreTolerance()) {
                drift.append(drift.isEmpty() ? "" : ", ")
                        .append(name).append(" computed ").append(computed.byName(name))
                        .append(" vs model ").append(claimed);
            }
        }
        if (!drift.isEmpty()) {
            log.info("AI scores differ from the computed scores by more than {} points: {}. The "
                            + "computed scores are what the product reports.",
                    settings.scoreTolerance(), drift);
        }
    }
}

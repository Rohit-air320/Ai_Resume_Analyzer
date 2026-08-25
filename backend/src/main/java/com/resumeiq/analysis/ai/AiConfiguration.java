package com.resumeiq.analysis.ai;

import com.resumeiq.config.ResumeIqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.client.ClientHttpRequestFactories;
import org.springframework.boot.web.client.ClientHttpRequestFactorySettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.Locale;

/**
 * Chooses which writer produces the advice, once, at startup.
 *
 * <p>The choice is configuration, not a runtime fallback: with {@code AI_PROVIDER=mock} or no key set,
 * the offline writer is the {@link AdviceSource} bean and no HTTP client is created at all. That is what
 * makes the offline path something the test suite exercises as the default rather than an emergency
 * branch nobody runs until production. Every test in this project runs in this mode, which is also why
 * no test can accidentally spend money or reach the network.
 *
 * <h2>A missing key is not a startup failure</h2>
 *
 * <p>Configuring a provider and forgetting the key logs a warning and starts in offline mode. Refusing to
 * start would be the stricter choice and the wrong one here: somebody who has just cloned this project
 * should get a working application on the first run, and a resume analyser that scores resumes without a
 * key is a working application. The warning says exactly what to set.
 */
@Configuration
public class AiConfiguration {

    private static final Logger log = LoggerFactory.getLogger(AiConfiguration.class);

    /** The one provider this project implements. Others are recognised only well enough to refuse. */
    private static final String ANTHROPIC = "anthropic";

    private final ResumeIqProperties.Ai settings;

    public AiConfiguration(ResumeIqProperties properties) {
        this.settings = properties.ai();
    }

    /**
     * The advice source the analysis service will use.
     *
     * <p>The offline writer is constructed here rather than being a bean of its own, and that is not a
     * style preference. {@link OfflineAdviceSource} implements {@link AdviceSource}, so publishing both
     * would leave two beans of that type in the context and {@link com.resumeiq.analysis.ResumeAnalyzer}
     * unable to say which it wanted — a startup failure, and one that only appears when the application
     * context is built. Exactly one {@code AdviceSource} exists, chosen here.
     *
     * <p>The startup log line is deliberate. "Which model is this actually calling, if any" is the first
     * question anybody asks when the advice looks wrong, and it should be answerable from the log rather
     * than by reading the configuration and guessing at precedence.
     */
    @Bean
    public AdviceSource adviceSource() {
        OfflineAdviceSource offline = new OfflineAdviceSource();
        String provider = settings.provider() == null
                ? "" : settings.provider().strip().toLowerCase(Locale.ROOT);

        if (!settings.callsAModel()) {
            if (!ResumeIqProperties.Ai.MOCK.equals(provider) && !settings.hasKey()) {
                log.warn("AI_PROVIDER is set to '{}' but AI_API_KEY is empty, so advice will be "
                        + "written by the offline writer. Scores are computed in Java either way and "
                        + "are unaffected. Set AI_API_KEY to enable model-written advice.", provider);
            } else {
                log.info("AI advice: {}. Scores are computed in Java and do not depend on a provider.",
                        settings.describe());
            }
            return offline;
        }

        if (!ANTHROPIC.equals(provider)) {
            log.warn("AI_PROVIDER '{}' is not implemented — this build supports 'anthropic' and "
                    + "'mock'. Falling back to the offline writer.", provider);
            return offline;
        }

        log.info("AI advice: {}, timeout {}s, up to {} attempt(s). The API key is never logged and "
                        + "never leaves the backend.",
                settings.describe(), settings.timeoutSeconds(), settings.maxRetries() + 1);
        return new AiAdviceSource(new AnthropicProvider(anthropicClient(), settings), offline,
                settings);
    }

    /**
     * The HTTP client for the provider.
     *
     * <p>Both timeouts are set from configuration and both matter. Without a read timeout a hung
     * provider holds a request thread until the container gives up, which turns one slow analysis into an
     * outage; the default of ninety seconds is generous because a long resume against a long posting is
     * a genuinely slow call.
     */
    private RestClient anthropicClient() {
        Duration timeout = Duration.ofSeconds(settings.timeoutSeconds());
        return RestClient.builder()
                .baseUrl(settings.baseUrl())
                .defaultHeader("x-api-key", settings.apiKey())
                .defaultHeader("content-type", "application/json")
                .requestFactory(ClientHttpRequestFactories.get(
                        ClientHttpRequestFactorySettings.DEFAULTS
                                .withConnectTimeout(Duration.ofSeconds(10))
                                .withReadTimeout(timeout)))
                .build();
    }
}

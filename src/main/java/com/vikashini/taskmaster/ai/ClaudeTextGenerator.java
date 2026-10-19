package com.vikashini.taskmaster.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.BetaTextBlock;
import com.anthropic.models.beta.messages.MessageCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.stream.Collectors;

/** {@link TextGenerator} backed by the Claude Messages API. */
class ClaudeTextGenerator implements TextGenerator, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ClaudeTextGenerator.class);

    /** Short outputs; the headroom covers the model's thinking before it answers. */
    private static final long MAX_TOKENS = 4_000;

    private final AnthropicClient client;
    private final String model;

    ClaudeTextGenerator(AnthropicClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public String generate(String instructions, String input) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(model)
                .maxTokens(MAX_TOKENS)
                .system(instructions)
                .addUserMessage(input)
                // Writing a task description is simple work; low effort keeps it quick and cheap.
                .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
                // If the model declines a request, let the API retry it on a suitable fallback model.
                .addBeta("server-side-fallback-2026-07-01")
                .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
                .build();

        BetaMessage response;
        try {
            response = client.beta().messages().create(params);
        } catch (AnthropicException e) {
            log.warn("Claude request failed: {}", e.getMessage());
            throw new AiUnavailableException("The AI service could not be reached; please try again later");
        }

        if (response.stopReason().filter(BetaStopReason.REFUSAL::equals).isPresent()) {
            throw new AiUnavailableException("The AI service declined to answer this request");
        }
        String text = response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(BetaTextBlock::text)
                .collect(Collectors.joining())
                .trim();
        if (text.isEmpty()) {
            throw new AiUnavailableException("The AI service returned an empty answer");
        }
        return text;
    }

    /** Spring calls this on shutdown, which releases the HTTP client's threads. */
    @Override
    public void close() {
        client.close();
    }
}

package com.vikashini.taskmaster.ai;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sets up the AI endpoints. Without an API key the application still starts and every
 * other feature works; the AI endpoints answer 503 and say how to turn them on.
 */
@Configuration
class AiConfig {

    @Bean
    TextGenerator textGenerator(Properties properties) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            return (instructions, input) -> {
                throw new AiUnavailableException("AI features are off; set ANTHROPIC_API_KEY to turn them on");
            };
        }
        return new ClaudeTextGenerator(
                AnthropicOkHttpClient.builder().apiKey(properties.apiKey()).build(), properties.model());
    }

    /** {@code app.ai.*} */
    @ConfigurationProperties("app.ai")
    record Properties(String apiKey, String model) {
    }
}

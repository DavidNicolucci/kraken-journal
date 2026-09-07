package dev.kraken.journal.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotBlank;

@Validated
@ConfigurationProperties(prefix = "anthropic")
public record AnthropicProperties(
        String baseUrl,
        @NotBlank String apiKey,
        String model,
        Integer maxTokens
) {
    public AnthropicProperties {
        if (baseUrl == null || baseUrl.isBlank()) baseUrl = "https://api.anthropic.com";
        if (model == null || model.isBlank()) model = "claude-sonnet-5";
        if (maxTokens == null) maxTokens = 4096;
    }
}

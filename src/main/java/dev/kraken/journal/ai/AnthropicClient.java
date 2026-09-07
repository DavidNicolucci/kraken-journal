package dev.kraken.journal.ai;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.ai.dto.AnthropicRequest;
import dev.kraken.journal.ai.dto.AnthropicResponse;
import dev.kraken.journal.config.AnthropicProperties;

@Component
public class AnthropicClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicClient.class);

    private final RestClient restClient;
    private final AnthropicProperties properties;

    public AnthropicClient(RestClient anthropicRestClient, AnthropicProperties properties) {
        this.restClient = anthropicRestClient;
        this.properties = properties;
    }

    public String complete(String systemPrompt, String userPrompt) {
        var request = new AnthropicRequest(
                properties.model(),
                properties.maxTokens(),
                systemPrompt,
                List.of(AnthropicRequest.Message.user(userPrompt)),
                null
        );

        AnthropicResponse response = restClient.post()
                .uri("/v1/messages")
                .body(request)
                .retrieve()
                .body(AnthropicResponse.class);

        if (response == null) {
            throw new IllegalStateException("Risposta vuota dall'API Anthropic");
        }
        if (response.usage() != null) {
            log.debug("Token usati: input={} output={}",
                    response.usage().input_tokens(), response.usage().output_tokens());
        }
        return response.text();
    }
}

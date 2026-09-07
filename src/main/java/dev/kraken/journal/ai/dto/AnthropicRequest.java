package dev.kraken.journal.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AnthropicRequest(
        String model,
        Integer max_tokens,
        String system,
        List<Message> messages,
        Double temperature
) {
    public record Message(String role, String content) {
        public static Message user(String content) {
            return new Message("user", content);
        }
    }
}

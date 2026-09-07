package dev.kraken.journal.ai.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnthropicResponse(
        String id,
        String model,
        List<ContentBlock> content,
        String stop_reason,
        Usage usage
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ContentBlock(String type, String text) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Usage(Integer input_tokens, Integer output_tokens) {}

    /** Concatena i soli blocchi testuali, ignorando eventuali blocchi di altro tipo. */
    public String text() {
        if (content == null) return "";
        return content.stream()
                .filter(b -> "text".equals(b.type()) && b.text() != null)
                .map(ContentBlock::text)
                .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}

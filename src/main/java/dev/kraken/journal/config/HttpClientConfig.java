package dev.kraken.journal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties({KrakenProperties.class, AnthropicProperties.class})
public class HttpClientConfig {

    private static SimpleClientHttpRequestFactory factory(Duration connect, Duration read) {
        var f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(connect);
        f.setReadTimeout(read);
        return f;
    }

    @Bean
    RestClient krakenRestClient(KrakenProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(20)))
                .build();
    }

    @Bean
    RestClient anthropicRestClient(AnthropicProperties props) {
        return RestClient.builder()
                .baseUrl(props.baseUrl())
                .requestFactory(factory(Duration.ofSeconds(5), Duration.ofSeconds(120)))
                .defaultHeader("x-api-key", props.apiKey())
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader("content-type", "application/json")
                .build();
    }
}

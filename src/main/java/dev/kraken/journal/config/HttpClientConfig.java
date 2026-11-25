package dev.kraken.journal.config;

import java.time.Duration;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import dev.kraken.journal.journal.ProprietaCommento;
import dev.kraken.journal.journal.ProprietaJournal;
import dev.kraken.journal.scansione.ProprietaScansione;
import dev.kraken.journal.simulatore.ProprietaSimulatore;

@Configuration
@EnableConfigurationProperties({KrakenProperties.class, ProprietaScansione.class, ProprietaJournal.class,
        ProprietaCommento.class, ProprietaSimulatore.class})
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
}

package com.kaankaplan.movieService.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * Obtient aupres de Keycloak le jeton de SERVICE de movie-service (flux Client Credentials, RFC 6749 4.4).
 * Le jeton est mis en cache et renouvele 30 secondes avant son expiration.
 */
@Component
public class ServiceTokenProvider {

    // Client non "load balanced" : Keycloak n'est pas enregistre dans Eureka
    private final WebClient webClient = WebClient.create();

    @Value("${keycloak.token-uri}")
    private String tokenUri;

    @Value("${keycloak.client-id}")
    private String clientId;

    @Value("${keycloak.client-secret}")
    private String clientSecret;

    private String token;
    private Instant expiresAt = Instant.EPOCH;

    public synchronized String getToken() {
        if (token == null || Instant.now().isAfter(expiresAt.minusSeconds(30))) {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);

            Map<String, Object> response = webClient.post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(BodyInserters.fromFormData(form))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(5));

            token = (String) response.get("access_token");
            expiresAt = Instant.now().plusSeconds(((Number) response.get("expires_in")).longValue());
        }
        return token;
    }
}

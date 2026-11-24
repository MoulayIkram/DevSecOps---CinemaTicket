package com.kaankaplan.movieService.client;

import com.kaankaplan.movieService.security.ServiceTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Appel machine a machine movieService -> userService.
 * Le Builder est @LoadBalanced (http://USERSERVICE est resolu par Eureka) ;
 * l'identite de l'appelant est le jeton de service, pas celui de l'utilisateur.
 */
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final WebClient.Builder webClientBuilder;
    private final ServiceTokenProvider tokenProvider;

    public Optional<Map<String, Object>> getProfile(String userId) {
        try {
            Map<String, Object> profile = webClientBuilder.build().get()
                    .uri("http://USERSERVICE/api/user/internal/users/{id}", userId)
                    .headers(h -> h.setBearerAuth(tokenProvider.getToken()))
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(Duration.ofSeconds(5));
            return Optional.ofNullable(profile);
        } catch (WebClientResponseException.NotFound e) {
            return Optional.empty();
        }
    }
}

package com.kaankaplan.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.server.SecurityWebFilterChain;
import reactor.core.publisher.Mono;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Le Gateway est un resource server OAuth2 : il valide les JWT émis par Keycloak
 * (signature RS256 via JWKS, issuer, expiration) et applique les règles par route.
 * Les services en aval revalident eux-mêmes le jeton (Zero Trust).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {

        http.cors().and().csrf().disable()
                .authorizeExchange(exchange -> exchange
                        // Pré-vols CORS
                        .pathMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        // Tableau de bord Eureka relayé par le Gateway (dev uniquement)
                        .pathMatchers("/eureka/**").permitAll()

                        // Catalogue : lecture publique
                        .pathMatchers(HttpMethod.GET, "/api/movie/**").permitAll()

                        // Commentaires et paiement : utilisateur connecté
                        .pathMatchers(HttpMethod.POST, "/api/movie/comments/**").hasAnyRole("CUSTOMER", "ADMIN")
                        .pathMatchers(HttpMethod.POST, "/api/movie/payments/**").hasAnyRole("CUSTOMER", "ADMIN")

                        // Toute autre écriture sur le catalogue : ADMIN
                        .pathMatchers("/api/movie/**").hasRole("ADMIN")

                        // Endpoints internes (service à service) : jamais exposés par le Gateway
                        .pathMatchers("/api/user/internal/**").denyAll()

                        .anyExchange().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(keycloakJwtConverter())));

        return http.build();
    }

    /** Transforme realm_access.roles (Keycloak) en autorités ROLE_xxx. */
    private Converter<Jwt, Mono<AbstractAuthenticationToken>> keycloakJwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractRoles);
        return new ReactiveJwtAuthenticationConverterAdapter(converter);
    }

    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (!(realmAccess instanceof Map)) {
            return Collections.emptyList();
        }
        Object roles = ((Map<String, Object>) realmAccess).get("roles");
        if (!(roles instanceof List)) {
            return Collections.emptyList();
        }
        return ((List<Object>) roles).stream()
                .map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
    }
}

package com.kaankaplan.emailService.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Map;

/**
 * Valide le jeton de service joint a un message Kafka : signature (JWKS de Keycloak),
 * issuer, expiration, et presence du role requis (SERVICE_MOVIE).
 */
@Component
public class ServiceTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(ServiceTokenValidator.class);

    private final String issuer;
    private final String requiredRole;
    private volatile JwtDecoder decoder;

    public ServiceTokenValidator(@Value("${cinevision.security.issuer-uri}") String issuer,
                                 @Value("${cinevision.security.required-role}") String requiredRole) {
        this.issuer = issuer;
        this.requiredRole = requiredRole;
    }

    // Cree a la premiere utilisation : Keycloak n'a pas besoin d'etre joignable au demarrage
    private JwtDecoder decoder() {
        if (decoder == null) {
            synchronized (this) {
                if (decoder == null) {
                    NimbusJwtDecoder d = NimbusJwtDecoder
                            .withJwkSetUri(issuer + "/protocol/openid-connect/certs").build();
                    d.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
                    decoder = d;
                }
            }
        }
        return decoder;
    }

    /** @param authorization valeur du header, au format "Bearer xxx" */
    public boolean isValid(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return false;
        }
        try {
            Jwt jwt = decoder().decode(authorization.substring("Bearer ".length()));
            Object realmAccess = jwt.getClaim("realm_access");
            if (realmAccess instanceof Map) {
                Object roles = ((Map<?, ?>) realmAccess).get("roles");
                return roles instanceof Collection && ((Collection<?>) roles).contains(requiredRole);
            }
            return false;
        } catch (Exception e) {
            log.warn("Jeton Kafka invalide : {}", e.getMessage());
            return false;
        }
    }
}

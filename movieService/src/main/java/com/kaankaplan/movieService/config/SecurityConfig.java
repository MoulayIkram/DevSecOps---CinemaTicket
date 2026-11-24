package com.kaankaplan.movieService.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * movieService est un resource server OAuth2 : il revalide lui-meme chaque JWT (Zero Trust),
 * meme quand la requete vient du Gateway. Les roles viennent de realm_access.roles (Keycloak).
 * L'ordre HIGHEST_PRECEDENCE place la securite AVANT le CircuitBreaker/Retry : un refus (403)
 * n'est donc ni retente ni masque par la methode fallback.
 */
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, order = Ordered.HIGHEST_PRECEDENCE)
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeRequests(a -> a
                        // Catalogue : lecture publique
                        .antMatchers(HttpMethod.GET, "/api/movie/**").permitAll()
                        // Commentaires et paiement : utilisateur connecte
                        .antMatchers(HttpMethod.POST, "/api/movie/comments/**", "/api/movie/payments/**")
                        .hasAnyRole("CUSTOMER", "ADMIN")
                        // Toute autre ecriture : ADMIN
                        .antMatchers("/api/movie/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(jwtAuthenticationConverter())));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::extractAuthorities);
        return converter;
    }

    /** JWT Keycloak -> ROLE_xxx (realm_access.roles) et SCOPE_xxx (claim scope). */
    private Collection<GrantedAuthority> extractAuthorities(Jwt jwt) {
        List<GrantedAuthority> authorities = new ArrayList<>();

        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map) {
            Object roles = ((Map<?, ?>) realmAccess).get("roles");
            if (roles instanceof Collection) {
                for (Object role : (Collection<?>) roles) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
                }
            }
        }

        String scope = jwt.getClaimAsString("scope");
        if (scope != null && !scope.isBlank()) {
            for (String s : scope.split(" ")) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + s));
            }
        }
        return authorities;
    }
}

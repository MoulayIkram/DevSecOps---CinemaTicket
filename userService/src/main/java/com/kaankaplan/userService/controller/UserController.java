package com.kaankaplan.userService.controller;

import com.kaankaplan.userService.dao.UserProfileDao;
import com.kaankaplan.userService.entity.UserProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final UserProfileDao profileDao;

    /** Profil de l'utilisateur connecte, cree a la premiere connexion a partir des claims du jeton. */
    @GetMapping("/users/me")
    @PreAuthorize("hasAnyRole('CUSTOMER','ADMIN')")
    public UserProfile me(@AuthenticationPrincipal Jwt jwt) {
        return profileDao.findById(jwt.getSubject()).orElseGet(() -> {
            String fullName = jwt.getClaimAsString("name");
            if (fullName == null) {
                fullName = jwt.getClaimAsString("preferred_username");
            }
            return profileDao.save(UserProfile.builder()
                    .userId(jwt.getSubject())
                    .email(jwt.getClaimAsString("email"))
                    .fullName(fullName)
                    .build());
        });
    }

    /**
     * Appel machine a machine : reserve au jeton de SERVICE de movie-service
     * (role SERVICE_MOVIE). Un jeton d'utilisateur, meme ADMIN, recoit 403.
     */
    @GetMapping("/internal/users/{userId}")
    @PreAuthorize("hasRole('SERVICE_MOVIE')")
    public UserProfile profile(@PathVariable String userId) {
        return profileDao.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Profil introuvable"));
    }
}

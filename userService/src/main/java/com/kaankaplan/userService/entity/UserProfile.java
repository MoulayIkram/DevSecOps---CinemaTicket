package com.kaankaplan.userService.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** Simple profil (sans mot de passe) : l'identite est geree par Keycloak. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "profiles")
public class UserProfile {

    @Id
    private String userId;   // = claim "sub" du jeton Keycloak
    private String email;
    private String fullName;
}

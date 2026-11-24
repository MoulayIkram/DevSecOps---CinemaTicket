import Keycloak from "keycloak-js";

// Client public "cinevision-frontend" du realm "cinevision" (Authorization Code + PKCE)
const keycloak = new Keycloak({
    url: "http://localhost:8181",
    realm: "cinevision",
    clientId: "cinevision-frontend",
});

// Construit l'objet utilisateur stocké dans Redux à partir du jeton Keycloak.
// Même forme qu'avant ({ token, userId, fullName, email, roles }) pour ne pas toucher aux pages.
export function buildUser(kc) {
    const t = kc.tokenParsed || {};
    const roles = ((t.realm_access && t.realm_access.roles) || [])
        .filter((r) => r === "ADMIN" || r === "CUSTOMER")
        .sort((a, b) => (a === "ADMIN" ? -1 : b === "ADMIN" ? 1 : 0)); // ADMIN en premier (roles[0])
    return {
        token: kc.token,
        userId: t.sub,
        fullName: t.name || t.preferred_username,
        email: t.email,
        roles,
    };
}

export default keycloak;

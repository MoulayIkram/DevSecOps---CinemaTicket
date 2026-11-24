# CineVision : authentification et autorisation inter-services

Projet Ingénierie des Systèmes Sécurisés, ENSIAS, A.U. 2026/2027.
Binôme : Ikram Moulay et Wissal Kallah.

L'application de départ est [CineVisionMicroserviceProject](https://github.com/VonHumbolt/CineVisionMicroserviceProject) de Kaan Kaplan
(microservices Spring Boot et frontend React de réservation de billets de cinéma).
Notre travail consiste à **sécuriser les échanges entre utilisateurs et services, puis entre les services eux-mêmes**, selon le principe Zero Trust :
chaque appel prouve son identité et ses droits, et chaque service revérifie lui-même ce qu'il reçoit.

Le rapport détaillé (état de l'art, comparatif mTLS / JWT / OAuth 2.0-OIDC / RBAC / ABAC / PBAC / ReBAC, justification, tests) est déposé sur Moodle.

---

## 1. Solution retenue

| Besoin | Mécanisme |
|---|---|
| Identité des utilisateurs | **Keycloak 24** (OAuth 2.0 / OpenID Connect), flux Authorization Code avec PKCE pour le frontend |
| Format des jetons | **JWT signé en RS256** ; les services vérifient la signature avec la clé publique de Keycloak (JWKS), sans aucun secret partagé |
| Droits des utilisateurs | **RBAC** : rôles `ADMIN` et `CUSTOMER`, lus dans le claim `realm_access.roles` |
| Droits fins | **ABAC** : un commentaire n'est supprimable que par son auteur (claim `sub`) ou par un `ADMIN` |
| Appels de service à service | **Client Credentials** : `movieService` obtient son propre jeton de service (rôle `SERVICE_MOVIE`) pour appeler `userService` |
| Messages Kafka | le jeton de service est joint dans un header ; `emailService` le valide avant d'envoyer l'email |
| Secrets | aucun secret dans le code : tout passe par un fichier `.env` ignoré par Git |


## 2. Architecture

Le schéma ci-dessous présente l'architecture microservices de l'application, incluant l'authentification via Keycloak, le routage par l'API Gateway et la communication asynchrone avec Kafka.

![Architecture de l'application](Architecture.png)

| Composant | Rôle | Port |
|---|---|---|
| Frontend React | interface, connexion via Keycloak (`keycloak-js`) | 3000 |
| API Gateway | point d'entrée, valide le JWT et applique les règles par route | 8080 |
| Keycloak | fournisseur d'identité, realm `cinevision` importé automatiquement | 8181 |
| Eureka | annuaire des services | 8761 |
| userService | profils utilisateurs (MongoDB) et endpoint interne | aléatoire |
| movieService | films, commentaires, paiements (PostgreSQL) | aléatoire |
| emailService | envoi de l'email du ticket (consomme Kafka) | aléatoire |
| PostgreSQL / MongoDB | bases de données | 5433 / 27018 |
| Kafka | messages `movieService` vers `emailService` | 9092 |
| Zipkin / Redis | traçage / cache | 9411 / 6379 |


## 3. Prérequis

| Outil | Version |
|---|---|
| JDK | 17 |
| IntelliJ IDEA | plugin Lombok et annotation processing activés |
| Docker Desktop | démarré avant de lancer les commandes |
| Node.js | 16 ou 18 |
| Git | récent |

## 4. Installation et lancement

### 4.1 Configurer les secrets (obligatoire)

```powershell
Copy-Item .env.example .env
```
Remplir `.env` (ce fichier n'est jamais commité) :

| Variable | Contenu |
|---|---|
| `EUREKA_USER`, `EUREKA_PASSWORD` | identifiants d'Eureka, à choisir (lettres et chiffres uniquement) |
| `MOVIE_SERVICE_CLIENT_SECRET` | secret du client Keycloak `movie-service`, défini dans `keycloak/realm-cinevision.json` |
| `SMTP_USERNAME`, `SMTP_PASSWORD` | compte Gmail et mot de passe d'application, sans espaces |


### 4.2 Infrastructure Docker

```powershell
docker compose up -d postgres mongodb kafka zipkin redis keycloak
docker ps
```
Six conteneurs doivent être en état `Up`. Keycloak met environ une minute à démarrer et importe le realm `cinevision` automatiquement.
Console d'administration : http://localhost:8181 (`admin` / `admin`, environnement de développement).

### 4.3 Services Spring Boot (dans IntelliJ)

1. File > Open, sélectionner le `pom.xml` **racine**, puis Open as Project ; SDK 17 ; Reload All Maven Projects.
2. Démarrer dans cet ordre, en attendant `Started ...` à chaque fois :
   `EurekaServerApplication`, `UserServiceApplication`, `MovieServiceApplication`, `EmailServiceApplication`, `ApiGatewayApplication`.
3. Sur http://localhost:8761 (identifiants du `.env`), quatre services doivent apparaître : USERSERVICE, MOVIESERVICE, EMAILSERVICE, API-GATEWAY.

### 4.4 Données de test (première fois seulement)

`MovieServiceApplication` doit avoir démarré au moins une fois (Hibernate crée les tables). Le script ne s'exécute **qu'une fois** :
```powershell
Get-Content scripts\seed-postgres.sql | docker exec -i cinevision-postgres psql -U postgres -d cine_vision
```
Puis redémarrer `MovieServiceApplication` (la liste vide a pu être mise en cache).

### 4.5 Frontend

```powershell
cd frontend
npm install        
npm start
```
Ouvrir http://localhost:3000 : le bouton de connexion redirige vers Keycloak. Les films sont **plus bas dans la page**  ,  sous l'onglet «Now Showing».

### 4.6 Comptes de démonstration (realm de développement)

| Compte | Mot de passe | Rôles |
|---|---|---|
| `admin@cinevision.test` | `Admin1234` | `ADMIN`, `CUSTOMER` |
| `customer@cinevision.test` | `Test1234` | `CUSTOMER` |

L'inscription libre est activée : un nouveau compte reçoit le rôle `CUSTOMER`.

## 5. Tests de sécurité

Le script `scripts/test-securite.ps1` rejoue les cas du plan de test. Prérequis : Keycloak, les cinq services et le Gateway démarrés, `.env` rempli.

```powershell
powershell -ExecutionPolicy Bypass -File scripts\test-securite.ps1
powershell -ExecutionPolicy Bypass -File scripts\test-securite.ps1 -AvecExpiration   # ajoute le test du jeton expiré (6 minutes)
```

| Groupe | Cas testés | Attendu |
|---|---|---|
| Gateway | lecture publique ; écriture sans jeton ; `CUSTOMER` sur une route `ADMIN` ; `ADMIN` ; jeton falsifié | 200 / 401 / 403 / 200 / 401 |
| Appel direct aux services | `movieService` sans jeton ; `movieService` avec un `CUSTOMER` sur une route `ADMIN` ; `userService` sans jeton | 401 / 403 / 401 |
| Service à service | profil créé depuis les claims ; endpoint interne sans jeton, avec un jeton `ADMIN`, avec le jeton de service | 200 / 401 / 403 / 200 |
| ABAC | un `CUSTOMER` supprime le commentaire d'un autre ; un `ADMIN` le supprime | 403 / 200 |
| Scénario nominal | paiement sans jeton ; paiement `CUSTOMER` (email envoyé via Kafka) | 401 / 200 |

## 6. Structure du dépôt

```
api-gateway/            Gateway réactif, resource server OAuth2
eureka-server/          annuaire des services
userService/            profils (MongoDB), endpoint interne protégé
movieService/           catalogue, commentaires, paiements, jeton de service
emailService/           consumer Kafka qui valide le jeton de service
frontend/               React, keycloak-js, intercepteur Axios
keycloak/               realm-cinevision.json (clients, rôles, comptes de test)
scripts/                données de test (seed-postgres.sql) et tests de sécurité (test-securite.ps1)
captures/               preuves avant / après sécurisation
docker-compose.yml      infrastructure (bases, Kafka, Keycloak, Zipkin, Redis)
.env.example            modèle de configuration des secrets
```




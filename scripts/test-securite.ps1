# Tests de securite CineVision (plan de travail, phase 5)
#
# Prerequis : Keycloak (docker compose up -d keycloak ...), Eureka, userService, movieService,
# emailService et api-gateway demarres, et un fichier .env a la racine du projet.
#
# Lancement (depuis la racine du projet) :
#   powershell -ExecutionPolicy Bypass -File scripts\test-securite.ps1
#   powershell -ExecutionPolicy Bypass -File scripts\test-securite.ps1 -AvecExpiration   (ajoute le test du jeton expire, 6 min d attente)

param([switch]$AvecExpiration)

$ErrorActionPreference = "Stop"
$GW = "http://localhost:8080"
$KC = "http://localhost:8181/realms/cinevision/protocol/openid-connect/token"
$envFile = Join-Path $PSScriptRoot "..\.env"
$script:ok = 0
$script:ko = 0

function Get-EnvValue($key) {
    $line = Get-Content $envFile | Where-Object { $_ -like "$key=*" } | Select-Object -First 1
    if (-not $line) { throw "Variable $key absente de .env" }
    return $line.Substring($key.Length + 1).Trim()
}

function Get-UserToken($user, $userPassword) {
    $r = Invoke-RestMethod -Method Post -Uri $KC -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "password"; client_id = "cinevision-postman"; username = $user; password = $userPassword }
    return $r.access_token
}

function Get-ServiceToken {
    $r = Invoke-RestMethod -Method Post -Uri $KC -ContentType "application/x-www-form-urlencoded" -Body @{
        grant_type = "client_credentials"; client_id = "movie-service"; client_secret = (Get-EnvValue "MOVIE_SERVICE_CLIENT_SECRET") }
    return $r.access_token
}

function Get-Claims($token) {
    $p = $token.Split(".")[1].Replace("-", "+").Replace("_", "/")
    switch ($p.Length % 4) { 2 { $p += "==" } 3 { $p += "=" } }
    return [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($p)) | ConvertFrom-Json
}

function Get-ServicePort($app) {
    $auth = [Convert]::ToBase64String([Text.Encoding]::ASCII.GetBytes((Get-EnvValue "EUREKA_USER") + ":" + (Get-EnvValue "EUREKA_PASSWORD")))
    $r = Invoke-RestMethod "http://localhost:8761/eureka/apps/$app" -Headers @{ Authorization = "Basic $auth"; Accept = "application/json" }
    return ($r.application.instance | Select-Object -First 1).port.'$'
}

# Renvoie uniquement le code HTTP
function Get-Status($method, $url, $token, $body) {
    $headers = @{}
    if ($token) { $headers["Authorization"] = "Bearer $token" }
    $params = @{ Method = $method; Uri = $url; Headers = $headers; UseBasicParsing = $true }
    if ($body) { $params["Body"] = $body; $params["ContentType"] = "application/json" }
    try {
        return [int](Invoke-WebRequest @params).StatusCode
    } catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        return -1
    }
}

function Check($name, $expected, $actual) {
    if ($actual -eq $expected) {
        Write-Host ("[OK]   {0} -> {1}" -f $name, $actual) -ForegroundColor Green
        $script:ok++
    } else {
        Write-Host ("[ECHEC] {0} -> attendu {1}, obtenu {2}" -f $name, $expected, $actual) -ForegroundColor Red
        $script:ko++
    }
}

Write-Host "== Jetons ==" -ForegroundColor Cyan
$admin = Get-UserToken "admin@cinevision.test" "Admin1234"
$customer = Get-UserToken "customer@cinevision.test" "Test1234"
$service = Get-ServiceToken
$customerSub = (Get-Claims $customer).sub
$movieUrl = "http://localhost:" + (Get-ServicePort "MOVIESERVICE")
$userUrl = "http://localhost:" + (Get-ServicePort "USERSERVICE")
$dir = '{"directorName":"Test securite"}'

Write-Host "`n== 1. Gateway : sans jeton, mauvais role, jeton falsifie ==" -ForegroundColor Cyan
Check "Lecture publique sans jeton" 200 (Get-Status GET "$GW/api/movie/movies/displayingMovies")
Check "Ecriture sans jeton" 401 (Get-Status POST "$GW/api/movie/directors/add" $null $dir)
Check "CUSTOMER sur une route ADMIN" 403 (Get-Status POST "$GW/api/movie/directors/add" $customer $dir)
Check "ADMIN sur une route ADMIN" 200 (Get-Status POST "$GW/api/movie/directors/add" $admin $dir)
$tampered = $admin.Substring(0, $admin.Length - 4) + "AAAA"
Check "Jeton falsifie" 401 (Get-Status POST "$GW/api/movie/directors/add" $tampered $dir)

Write-Host "`n== 2. Appel direct aux services (contournement du Gateway) ==" -ForegroundColor Cyan
Check "movieService direct, sans jeton" 401 (Get-Status POST "$movieUrl/api/movie/directors/add" $null $dir)
Check "movieService direct, CUSTOMER sur route ADMIN" 403 (Get-Status POST "$movieUrl/api/movie/directors/add" $customer $dir)
Check "userService direct, sans jeton" 401 (Get-Status GET "$userUrl/api/user/users/me")

Write-Host "`n== 3. Appel service a service (Client Credentials) ==" -ForegroundColor Cyan
Check "Profil utilisateur (creation depuis les claims)" 200 (Get-Status GET "$GW/api/user/users/me" $customer)
Check "Endpoint interne sans jeton" 401 (Get-Status GET "$userUrl/api/user/internal/users/$customerSub")
Check "Endpoint interne avec un jeton ADMIN (pas un service)" 403 (Get-Status GET "$userUrl/api/user/internal/users/$customerSub" $admin)
Check "Endpoint interne avec le jeton de service" 200 (Get-Status GET "$userUrl/api/user/internal/users/$customerSub" $service)
Check "Endpoint interne via le Gateway (bloque)" 403 (Get-Status GET "$GW/api/user/internal/users/$customerSub" $admin)

Write-Host "`n== 4. ABAC : suppression d'un commentaire ==" -ForegroundColor Cyan
$c = Invoke-RestMethod -Method Post -Uri "$GW/api/movie/comments/add" -Headers @{ Authorization = "Bearer $admin" } -ContentType "application/json" -Body '{"movieId":1,"commentText":"Commentaire de test"}'
$del = '{"commentId":' + $c.commentId + '}'
Check "CUSTOMER supprime le commentaire d'un autre" 403 (Get-Status POST "$GW/api/movie/comments/delete" $customer $del)
Check "ADMIN supprime ce commentaire" 200 (Get-Status POST "$GW/api/movie/comments/delete" $admin $del)

Write-Host "`n== 5. Scenario nominal : paiement, jeton de service, Kafka ==" -ForegroundColor Cyan
$ticket = '{"movieName":"Film de test","saloonName":"Salle 1","movieDay":"2026-11-25","movieStartTime":"20:00","chairNumbers":"A1"}'
Check "Paiement sans jeton" 401 (Get-Status POST "$GW/api/movie/payments/sendTicketDetail" $null $ticket)
Check "Paiement CUSTOMER (email envoye via Kafka)" 200 (Get-Status POST "$GW/api/movie/payments/sendTicketDetail" $customer $ticket)
Write-Host "   -> verifier la console emailService : l'email doit partir (pas de ligne 'REJETE')." -ForegroundColor Yellow

if ($AvecExpiration) {
    Write-Host "`n== 6. Jeton expire (attente 375 s : duree de vie 300 s + tolerance d horloge de 60 s) ==" -ForegroundColor Cyan
    Start-Sleep -Seconds 375
    Check "Jeton expire" 401 (Get-Status GET "$GW/api/user/users/me" $customer)
}

Write-Host ("`nResultat : {0} reussis, {1} en echec" -f $script:ok, $script:ko) -ForegroundColor Cyan
if ($script:ko -gt 0) { exit 1 }

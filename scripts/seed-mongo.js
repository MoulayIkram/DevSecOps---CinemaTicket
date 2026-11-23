// Rôles utilisés par userService (base "user"). À exécuter UNE SEULE FOIS.
db.claim.insertMany([{ claimName: "CUSTOMER" }, { claimName: "ADMIN" }]);

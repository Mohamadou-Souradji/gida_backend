# Gida Jari — Backend (Spring Boot)

Plateforme BTP « La maison de demain » : 6 profils, séquestre (escrow), KYC progressif, OTP SMS.

Références :
- `Cahier_des_Charges_Technique_Gida_Jari_UML_MCD_SpringBoot_Angular.pdf` (MCD, UML, roadmap 50 %, stack) : **référence principale**
- `GJ_00_Architecture_Globale_V3_DEFINITIF.docx` (système de confiance, escrow, KYC, Mobile Money)
- `Gida_Jari_Document_Reference_Complet-2.docx` (parcours terrain, 6 profils)

## Stack

Java 17, Spring Boot 3.3, PostgreSQL, Flyway, Spring Data JPA/Hibernate, Spring Security + JWT, `@Scheduled`.

## Démarrage

```
mvn spring-boot:run
```

Il faut Java 17+ et une base PostgreSQL `gida_jari` (utilisateur `gida_jari`, voir `application.yml`).
Flyway crée le schéma au démarrage : V1, V2, V3, puis V4 (back-office).

Toute la configuration sensible se surcharge par variables d'environnement :

| Variable | Rôle | Défaut |
|---|---|---|
| `GJ_DB_URL` / `GJ_DB_USER` / `GJ_DB_PASSWORD` | Base PostgreSQL | localhost / gida_jari / change_me |
| `GJ_JWT_SECRET` | Clé de signature JWT (≥ 32 caractères) | valeur de dev **à changer** |
| `GJ_ADMIN_PHONES` | Super-admins d'office (amorçage du back-office), séparés par des virgules. Les autres membres de l'équipe sont ajoutés depuis l'écran Équipe | `+22790000000` |
| `GJ_CORS_ORIGINES` | URL du frontend | `http://localhost:4200` |
| `GJ_SMS_MODE` | `simulation` (code OTP affiché en console) ou `http` (agrégateur SMS) | `simulation` |
| `GJ_MOMO_MODE` | `simulation` (paiements confirmés immédiatement) ou `operateur` | `simulation` |
| `GJ_MOMO_WEBHOOK_SECRET` | Secret du callback opérateur (`X-GJ-Signature`) | à changer |

**Premier test.** Connectez-vous avec le numéro `90 00 00 00`, qui est super-admin par défaut (menu Administration). Le code OTP s'affiche dans la console du backend (`>>> CODE OTP`).

## Ce que couvre l'application

### Règles de gestion du Cahier des Charges (section 1.1)

| Règle | Implémentation |
|---|---|
| 1. Transit obligatoire par le séquestre | `EscrowService.payer` : le client paie par Mobile Money vers Gida Jari. Le prestataire n'est crédité que tranche par tranche (`libererTranche`), sur son solde Gida Jari, puis il retire (`CompteService.retirer`). Les téléphones ne sont jamais exposés dans les profils publics, et la messagerie est interne (anti-contournement). |
| 2. Commission différée 2 % / 1,5 % / 1 % | Prélevée à chaque libération de tranche ; montant et net enregistrés sur `escrow_milestone`. |
| 3. Auto-validation 48 h | `JalonAutoValidationScheduler` (toutes les heures) valide le jalon **et libère les fonds**. Rien n'est libéré pendant un litige. |
| 4. KYC obligatoire | `KYCService.exigerNiveau` (HTTP 403 `KYC_INSUFFISANT`). Niveau 1 = OTP, niveau 2 = profil complété (automatique), niveaux 3 à 6 = document validé par un admin. Le niveau 2 est exigé pour publier un projet ou un AO, pour payer, et pour envoyer une proposition. Le niveau 3 est exigé pour candidater à un AO et pour être engagé ou recevoir des fonds. |
| 5. Authentification par téléphone | OTP SMS haché (BCrypt), 5 min, usage unique. Limité à 5 essais par code et 3 demandes par 10 min. Les numéros sont normalisés au format +227. |

### Machine à états de l'escrow (section 1.3)

```
DRAFT --payer--> PENDING_PAYMENT --confirmation operateur--> FUNDED --1re tranche--> PARTIAL_RELEASE --derniere tranche--> COMPLETED
                         | echec -> DRAFT                FUNDED / PARTIAL_RELEASE --litige--> DISPUTED --admin--> RESOLVED_CLIENT | RESOLVED_PROVIDER | REFUNDED
```

### Roadmap 50 % par profil (section 2), avec les endpoints

| Profil | Fonctionnalités | API |
|---|---|---|
| Tous | OTP, compte, solde/retrait, notifications, KYC, messagerie, portfolio, notation | `/api/auth`, `/api/compte`, `/api/kyc`, `/api/messagerie`, `/api/portfolio`, `/api/notations`, `/api/files` |
| Client | Profil, projets + jalons, publication, engagement d'un prestataire, paiement séquestre, validation/rejet des jalons, propositions d'études reçues | `/api/clients/**`, `/api/projets/**`, `/api/escrow/**` |
| Artisan | Profil (métier, zone), disponibilité, recherche par les clients, missions, preuve photo GPS | `/api/artisans/**`, `/api/missions/**` |
| Entreprise BTP | Profil (NIF, RCCM), pièces via KYC, recherche d'AO, candidature, missions | `/api/entreprises-btp/**`, `/api/missions/**` |
| ONG / Institution | Profil institutionnel, publication d'AO, dépouillement (en évaluation / retenue / rejetée, attribution automatique), clôture | `/api/ong-institutions/**` |
| Ingénieur / Architecte | Profil expert, grille tarifaire, propositions d'études par phases (brouillon → envoi → acceptation/refus par le client) | `/api/experts/**` |
| Fournisseur | Boutique, zones et frais de livraison, CRUD catalogue, recherche de produits | `/api/fournisseurs/**` |
| Admin | Tableau de bord, projets, transactions (séquestre + retraits, export CSV), file KYC, arbitrage des litiges, équipe à rôles distincts, journal d'audit (`AdminActionLog`) | `/api/admin/**` |

Les endpoints sont protégés par profil (`@PreAuthorize`) et par propriétaire : on ne peut modifier que son propre profil, catalogue, AO, proposition, etc. Les corps de requête sont des DTO (`dto/Requetes`), jamais des entités, ce qui évite le *mass assignment*.

### MCD (17 entités + 2)

Les 17 entités du Cahier des Charges, plus `PropositionEtude` et `PhaseEtude`. Héritage `User` → 6 profils (JOINED). Diagrammes formels dans `docs/uml/`.
`WhatsAppSession` est modélisée mais le pont WhatsApp n'est pas encore branché (voir les limites ci-dessous).

## Back-office et rôles d'administration

| Rôle | Consultation (tableau de bord, projets, transactions, journal) | KYC | Litiges + export CSV | Équipe |
|---|---|---|---|---|
| `SUPER_ADMIN` | oui | oui | oui | oui |
| `OPERATEUR_KYC` | oui | oui | non | non |
| `GESTIONNAIRE_FINANCES` | oui | non | oui | non |
| `LECTEUR` | oui | non | non | non |

- Les numéros de `GJ_ADMIN_PHONES` sont `SUPER_ADMIN` d'office. Ils ne sont pas modifiables depuis l'interface, ce qui garantit qu'on ne perd jamais l'accès.
- Les autres membres sont dans la table `admin_membre`.
- Le rôle est relu à chaque requête : un retrait ou un changement de rôle s'applique immédiatement, sans attendre l'expiration du jeton.
- Une personne ajoutée à l'équipe doit se reconnecter une fois.
- Les retraits Mobile Money sont historisés dans `retrait_compte` (V4).

Endpoints : `GET /api/admin/moi`, `/tableau-de-bord`, `/projets[/{id}]`, `/transactions[/{id}]`, `/transactions/export`, `/retraits`, `/equipe` (GET, POST, PATCH, DELETE).

## Arborescence

```
src/main/java/com/gidajari/
  config/            SecurityConfig (JWT, CORS, roles), WebConfig (fichiers), JacksonConfig (proxys Hibernate)
  controller/        un controleur par profil + Auth, Compte, Projet, Mission, Escrow, KYC, Admin,
                     Messagerie, Notation, Portfolio, File, PaiementCallback
  dto/               Requetes (entrees validees), Vues (reponses composees), DTO d'authentification
  entity/            19 entites + enums (dont OperateurMobileMoney)
  integration/       SmsGateway (simulation | http), MobileMoneyGateway (simulation | operateur)
  repository/        Spring Data JPA
  security/          JwtTokenProvider, JwtAuthenticationFilter (ROLE_<PROFIL>, ROLE_ADMIN), CurrentUserProvider
  service/           EscrowService, ProjetService, KYCService, OTPService, NotificationService,
                     NotationService, MessagerieService, CompteService, AdminJournalService,
                     FileStorageService, JalonAutoValidationScheduler
src/main/resources/db/migration/   V1 (schema), V2 (propositions d'etudes), V3 (finalisation : escrow, litiges, index), V4 (equipe admin, retraits)
src/test/java/                     tests unitaires (escrow, KYC, OTP, notation)
scripts/parcours_api.py            parcours de bout en bout de l'API (258 verifications, back-office inclus)
docs/uml/                          diagrammes UML (classes, etats, cas d'utilisation)
```

## Tests

```
mvn test                         # 21 tests unitaires : regles 1 a 5, machine a etats, litiges, notation, roles admin
python scripts/parcours_api.py   # sur une base vide, backend lance avec "> backend.log"
```

## Limites connues (dépendances externes et V2)

- **Mobile Money réel** : `OperateurMobileMoneyGateway` est le point d'intégration (Airtel Money, Moov Money, Zamani Cash, Nita, Amana). Il faut les accès API partenaires. Le webhook `POST /api/public/paiements/callback` est prêt.
- **SMS réel** : passer `GJ_SMS_MODE=http` et adapter `HttpSmsGateway` au contrat de l'agrégateur retenu.
- **Stockage fichiers** : local (`./uploads`). À remplacer par un stockage objet en production.
- **Pont WhatsApp** (`WhatsAppSession`) et **mode hors-ligne 2G/3G** : prévus dans l'Architecture V3, hors périmètre de ce socle.
- La pondération de la notation (30/20/15/20/15) est configurable dans `application.yml`. Elle est à aligner sur la section 5.2 de l'Architecture V3.

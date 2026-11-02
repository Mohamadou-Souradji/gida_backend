# Gida Jari — Diagrammes UML (livrable formel)

Ces diagrammes repondent explicitement a la section 1 du Cahier des Charges
Technique ("Conception & Modelisation UML/MCD"), qui demande de **produire**
un diagramme de classes, un diagramme d'etats et une cartographie des cas
d'utilisation — en tant que livrables, en plus du code qui les implemente.

Sources : fichiers `.dot` (Graphviz), regenerables avec `dot -Tsvg fichier.dot -o fichier.svg`.
Chaque diagramme est fourni en `.svg` (vectoriel) et `.png` (previsualisation rapide).

## 01 — Diagramme de classes : profils utilisateurs

`01-class-diagram-profils.svg`

Superclasse unique `User` et heritage vers les 6 profils (Client, Artisan,
Entreprise BTP, Ingenieur/Architecte, Fournisseur, ONG/Institution), avec
leurs attributs specifiques. Correspond au Cahier des Charges Technique,
section 1.2.

## 02 — Diagramme de classes : entites metier

`02-class-diagram-domaine.svg`

Les entites P1/P2/P3 du MCD (Projet, Jalon, Escrow, KYC, Notation,
Conversation, AppelOffre, CatalogProduit...) et leurs relations
(compositions, agregations, associations vers `User`). Inclut
`PropositionEtude`/`PhaseEtude`, ajoutees pour couvrir integralement la
roadmap 50% du profil Ingenieur/Architecte. Correspond au Cahier des
Charges Technique, section 1.1.

## 03 — Diagramme d'etats : Escrow

`03-etat-escrow.svg`

Machine a etats complete du sequestre (escrow) : `DRAFT` → `PENDING_PAYMENT`
→ `FUNDED` → `PARTIAL_RELEASE` → `COMPLETED`, avec la branche litige
(`DISPUTED` → `RESOLVED_CLIENT` / `RESOLVED_PROVIDER` / `REFUNDED`). Chaque
transition est annotee avec la methode `EscrowService` qui la declenche.
Correspond au Cahier des Charges Technique, section 1.3.

## 04 — Cas d'utilisation par profil

`04-cas-utilisation.svg`

Cartographie des interactions pour chacun des 6 profils, alignee sur la
roadmap "50% par profil" (section 2 du Cahier des Charges Technique). Les
relations `<<include>>` indiquent les actions bloquees par un niveau KYC
minimum (regle de gestion n4).

## Regenerer les diagrammes

```bash
cd docs/uml
for f in *.dot; do dot -Tsvg "$f" -o "${f%.dot}.svg"; dot -Tpng "$f" -o "${f%.dot}.png"; done
```

Necessite Graphviz (`apt install graphviz` / `brew install graphviz`).

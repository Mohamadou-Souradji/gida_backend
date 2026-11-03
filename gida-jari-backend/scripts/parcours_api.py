"""
Parcours de bout en bout de l'API Gida Jari, back-office inclus.

Prerequis : base de donnees VIDE, backend demarre en mode simulation (SMS + Mobile Money)
avec le numero +22790000000 declare administrateur, et sa sortie console redirigee
dans un fichier :

    mvn spring-boot:run > backend.log 2>&1        (GJ_ADMIN_PHONES=+22790000000 par defaut)
    python scripts/parcours_api.py                 (GJ_API / GJ_LOG pour changer l'URL ou le log)
"""
import json, re, sys, urllib.request, urllib.error, uuid

import os
BASE = os.environ.get("GJ_API", "http://localhost:8080")
# Fichier de log du backend (le code OTP y est ecrit en mode sms simulation).
LOG = os.environ.get("GJ_LOG", "backend.log")
ok_count = 0
fails = []


def call(method, path, token=None, body=None, expect=200, raw=None, headers=None):
    global ok_count
    data = None
    h = {"Content-Type": "application/json"}
    if headers:
        h.update(headers)
    if raw is not None:
        data = raw
    elif body is not None:
        data = json.dumps(body).encode()
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=data, method=method, headers=h)
    try:
        with urllib.request.urlopen(req) as r:
            status, txt = r.status, r.read().decode()
    except urllib.error.HTTPError as e:
        status, txt = e.code, e.read().decode()
    res = json.loads(txt) if txt and txt.strip()[:1] in "[{" else txt
    exp = expect if isinstance(expect, (list, tuple)) else [expect]
    if status in exp:
        ok_count += 1
    else:
        fails.append(f"{method} {path} -> {status} (attendu {expect}) : {txt[:300]}")
    return res


def email_de(phone):
    return "u" + phone.replace(" ", "").replace("+", "")[-8:] + "@test.gidajari.com"


def login(phone, profil):
    """Connexion par code envoye par email : a l'inscription le serveur demande d'abord l'email."""
    r = call("POST", "/api/auth/otp/demander", body={"phone": phone}, expect=[200, 202])
    if isinstance(r, dict) and r.get("emailRequis"):
        call("POST", "/api/auth/otp/demander", body={"phone": phone, "email": email_de(phone)}, expect=202)
    norm = phone.replace(" ", "")
    norm = norm if norm.startswith("+") else "+227" + norm
    code = [l for l in open(LOG, encoding="utf-8", errors="ignore") if "CODE OTP" in l and norm in l][-1].split()[-1]
    r = call("POST", "/api/auth/otp/verifier", body={"phone": phone, "code": code, "profileType": profil})
    return r["token"], r["userId"], r


def check(cond, msg):
    global ok_count
    if cond:
        ok_count += 1
    else:
        fails.append("CHECK: " + msg)


def upload(token, name="photo.jpg"):
    boundary = "----gj" + uuid.uuid4().hex
    body = (f"--{boundary}\r\nContent-Disposition: form-data; name=\"fichier\"; filename=\"{name}\"\r\n"
            f"Content-Type: image/jpeg\r\n\r\n").encode() + b"\xff\xd8\xff fake" + f"\r\n--{boundary}--\r\n".encode()
    return call("POST", "/api/files", token, raw=body,
                headers={"Content-Type": f"multipart/form-data; boundary={boundary}"})


# ---------------------------------------------------------------- comptes
admT, admId, admR = login("+227 90 00 00 00", "CLIENT_PARTICULIER")
check(admR["admin"] is True, "admin flag")
cliT, cliId, cliR = login("90000001", "CLIENT_PARTICULIER")
check(cliR["kycLevel"] == "NIVEAU_1_TELEPHONE_VERIFIE", "OTP -> NIVEAU_1")
artT, artId, _ = login("90000002", "ARTISAN")
btpT, btpId, _ = login("90000003", "ENTREPRISE_BTP")
ongT, ongId, _ = login("90000004", "ONG_INSTITUTION")
expT, expId, _ = login("90000005", "INGENIEUR_ARCHITECTE")
fouT, fouId, _ = login("90000006", "FOURNISSEUR_MATERIAUX")
fou2T, fou2Id, _ = login("90000007", "FOURNISSEUR_MATERIAUX")

# ---------------------------------------------------------------- code par email (V5)
r = call("POST", "/api/auth/otp/demander", body={"phone": "90000098"}, expect=200)
check(r["emailRequis"] is True and r["nouvelUtilisateur"] is True and r["canal"] == "EMAIL", f"inscription : email demande ({r})")
check(not any("+22790000098" in l for l in open(LOG, encoding="utf-8", errors="ignore") if "CODE OTP" in l), "aucun code envoye sans email")
call("POST", "/api/auth/otp/demander", body={"phone": "90000098", "email": "pas-un-email"}, expect=400)
call("POST", "/api/auth/otp/demander", body={"phone": "90000098", "email": "u90000001@TEST.gidajari.com"}, expect=409)
r = call("POST", "/api/auth/otp/demander", body={"phone": "90000098", "email": "  Nouveau.Compte@Gmail.com "}, expect=202)
check(r["emailMasque"] == "n***e@gmail.com" and r["emailRequis"] is False, f"email masque ({r})")
ligne = [l for l in open(LOG, encoding="utf-8", errors="ignore") if "CODE OTP" in l and "+22790000098" in l][-1]
check("(email nouveau.compte@gmail.com)" in ligne, "code envoye a l'email normalise")
call("POST", "/api/auth/otp/verifier", body={"phone": "90000098", "code": ligne.split()[-1]}, expect=400)  # profil manquant
call("POST", "/api/auth/otp/demander", body={"phone": "90000098", "email": "nouveau.compte@gmail.com"}, expect=202)
ligne = [l for l in open(LOG, encoding="utf-8", errors="ignore") if "CODE OTP" in l and "+22790000098" in l][-1]
nvR = call("POST", "/api/auth/otp/verifier", body={"phone": "90000098", "code": ligne.split()[-1], "profileType": "ARTISAN"})
nvMoi = call("GET", "/api/compte/moi", nvR["token"])
check(nvMoi["email"] == "nouveau.compte@gmail.com" and nvMoi["kycLevel"] == "NIVEAU_1_TELEPHONE_VERIFIE", f"compte cree avec son email ({nvMoi})")
# compte existant : le code part sur l'email enregistre, un autre email saisi est ignore
r = call("POST", "/api/auth/otp/demander", body={"phone": "90000001", "email": "pirate@evil.com"}, expect=202)
check(r["nouvelUtilisateur"] is False and r["emailMasque"] == "u***1@test.gidajari.com", f"connexion : email du compte ({r})")
ligne = [l for l in open(LOG, encoding="utf-8", errors="ignore") if "CODE OTP" in l and "+22790000001" in l][-1]
check("pirate@evil.com" not in ligne and "u90000001@test.gidajari.com" in ligne, "email du compte non modifiable a la connexion")
check(call("GET", "/api/compte/moi", cliT)["email"] == "u90000001@test.gidajari.com", "email dans /compte/moi")
check("email" not in call("GET", f"/api/clients/{cliId}", artT), "email non expose dans le profil public")

# mauvais code OTP
call("POST", "/api/auth/otp/demander", body={"phone": "90000099", "email": "u90000099@test.gidajari.com"}, expect=202)
call("POST", "/api/auth/otp/verifier", body={"phone": "90000099", "code": "0000000", "profileType": "ARTISAN"}, expect=401)

# securite de base
call("GET", "/api/compte/moi", expect=401)
call("GET", "/api/admin/kyc/en-attente", cliT, expect=403)
call("GET", "/api/clients/projets", artT, expect=403)
call("PUT", f"/api/clients/{cliId}", admT, {"nom": "Pirate"}, expect=403)

moi = call("GET", "/api/compte/moi", cliT)
check(moi["phone"] == "+22790000001", "phone normalise dans /compte/moi")

# ---------------------------------------------------------------- profils -> NIVEAU_2
c = call("PUT", f"/api/clients/{cliId}", cliT, {"nom": "Issa", "prenom": "Moussa", "adresse": "Niamey", "typeClient": "particulier"})
check("phone" not in c, "telephone non expose dans le profil public")
check(call("GET", "/api/compte/moi", cliT)["kycLevel"] == "NIVEAU_2_IDENTITE_DECLAREE", "profil complet -> NIVEAU_2")
call("PUT", f"/api/artisans/{artId}", artT, {"nom": "Ali", "prenom": "Sani", "corpsDeMetier": "macon", "zoneIntervention": "Niamey", "tarifJournalierFcfa": 8000})
call("PATCH", "/api/artisans/disponibilite?statut=AVAILABLE_NOW", artT)
call("PUT", f"/api/entreprises-btp/{btpId}", btpT, {"nomCommercial": "BTP Sahel", "nif": "N123", "rccm": "R456", "domaineActivite": "gros oeuvre", "effectif": 25})
call("PUT", f"/api/ong-institutions/{ongId}", ongT, {"raisonSociale": "ONG Eau Pour Tous", "nifOuRccm": "R789", "typeOrganisation": "ONG"})
call("PUT", f"/api/experts/{expId}", expT, {"nom": "Diallo", "prenom": "Hamid", "specialite": "structure", "numeroOrdre": "ORD-12"})
call("PATCH", "/api/experts/grille-tarifaire", expT, raw=b'{"tarifHoraireFcfa":15000}')
call("PUT", f"/api/fournisseurs/{fouId}", fouT, {"nomBoutique": "Quincaillerie Zarma", "nif": "F1", "zonesLivraison": "Niamey", "fraisLivraisonFcfa": 2000})
call("GET", f"/api/clients/{cliId}", artT)
call("GET", f"/api/artisans/{artId}", cliT)

# ---------------------------------------------------------------- KYC niveau 3 (admin)
f = upload(artT)
check(isinstance(f, dict) and f.get("url", "").startswith("/files/"), "upload fichier")
call("POST", "/api/files", artT, raw=b"x", headers={"Content-Type": "multipart/form-data; boundary=zz"}, expect=[400, 500])
call("POST", "/api/kyc/soumissions", artT, {"niveauVise": "NIVEAU_2_IDENTITE_DECLAREE", "typeDocument": "CNI", "documentUrl": f["url"]}, expect=400)
s1 = call("POST", "/api/kyc/soumissions", artT, {"niveauVise": "NIVEAU_3_PIECE_IDENTITE", "typeDocument": "CNI", "documentUrl": f["url"]})
s2 = call("POST", "/api/kyc/soumissions", btpT, {"niveauVise": "NIVEAU_3_PIECE_IDENTITE", "typeDocument": "RCCM", "documentUrl": f["url"]})
attente = call("GET", "/api/admin/kyc/en-attente", admT)
check(len(attente) == 2, "2 KYC en attente")
call("PATCH", f"/api/admin/kyc/{s1['id']}/approuver", admT)
call("PATCH", f"/api/admin/kyc/{s1['id']}/approuver", admT, expect=409)
call("PATCH", f"/api/admin/kyc/{s2['id']}/approuver", admT)
check(call("GET", "/api/compte/moi", artT)["kycLevel"] == "NIVEAU_3_PIECE_IDENTITE", "artisan NIVEAU_3")

# ---------------------------------------------------------------- projet + jalons
p = call("POST", "/api/clients/projets", cliT, {"titre": "Maison F3", "typeProjet": "construction", "localisation": "Niamey",
                                                 "budgetEstimeFcfa": 1500000,
                                                 "jalons": [{"libelle": "Fondations", "montantFcfa": 500000},
                                                            {"libelle": "Elevation", "montantFcfa": 1000000}]})
pid = p["id"]
call("POST", "/api/clients/projets", cliT, {"titre": "", "typeProjet": "x", "localisation": "y"}, expect=400)
call("POST", f"/api/clients/projets/{pid}/engager", cliT, {"beneficiaireId": artId}, expect=409)  # pas publie
call("POST", f"/api/clients/projets/{pid}/publier", cliT)
ouverts = call("GET", "/api/projets/ouverts", artT)
check(any(x["id"] == pid for x in ouverts), "projet visible dans /projets/ouverts")
r = call("GET", "/api/artisans/recherche?corpsDeMetier=mac&disponibleSeulement=true", cliT)
check(any(a["id"] == artId for a in r), "recherche artisan")

# ---------------------------------------------------------------- sequestre
call("POST", f"/api/clients/projets/{pid}/engager", cliT, {"beneficiaireId": expId}, expect=400)  # expert non engageable
t = call("POST", f"/api/clients/projets/{pid}/engager", cliT, {"beneficiaireId": artId})
check(t["statut"] == "DRAFT" and t["montantTotalFcfa"] == 1500000, "escrow DRAFT 1 500 000")
call("POST", f"/api/clients/projets/{pid}/engager", cliT, {"beneficiaireId": artId}, expect=409)
call("POST", f"/api/clients/projets/{pid}/jalons", cliT, [{"libelle": "x", "montantFcfa": 1}], expect=409)
t = call("POST", f"/api/clients/escrow/{t['id']}/payer", cliT, {"operateur": "AMANA", "numero": "+22790000001"})
check(t["statut"] == "FUNDED", "paiement simule -> FUNDED")
tid = t["id"]

missions = call("GET", "/api/missions", artT)
check(len(missions) == 1, "1 mission artisan")
jalons = sorted(missions[0]["projet"]["jalons"], key=lambda j: j["ordre"])
j1, j2 = jalons[0]["id"], jalons[1]["id"]

call("POST", "/api/missions/jalons/preuve", btpT, {"jalonId": j1, "photoUrl": f["url"], "latitude": 13.5, "longitude": 2.1}, expect=403)
call("POST", "/api/missions/jalons/preuve", artT, {"jalonId": j1, "photoUrl": f["url"], "latitude": 13.51, "longitude": 2.11})
call("POST", f"/api/clients/jalons/{j1}/valider", admT, expect=403)  # autre client
call("POST", f"/api/clients/jalons/{j1}/valider", cliT)
check(call("GET", "/api/compte/moi", artT)["walletBalance"] == 490000, "artisan credite 500000 - 2% = 490000")
call("POST", f"/api/clients/jalons/{j2}/valider", cliT, expect=409)  # pas soumis
call("POST", "/api/missions/jalons/preuve", artT, {"jalonId": j2, "photoUrl": f["url"], "latitude": 13.5, "longitude": 2.1})
call("POST", f"/api/clients/jalons/{j2}/rejeter", cliT, {"motif": "Photo floue"})
call("POST", "/api/missions/jalons/preuve", artT, {"jalonId": j2, "photoUrl": f["url"], "latitude": 13.5, "longitude": 2.1})
call("POST", f"/api/clients/jalons/{j2}/valider", cliT)
t = call("GET", f"/api/escrow/{tid}", cliT)
check(t["statut"] == "COMPLETED" and t["montantLibereFcfa"] == 1500000, "escrow COMPLETED")
check(sum(m["commissionFcfa"] for m in t["milestones"]) == 30000, "commission totale 30000")
call("GET", f"/api/escrow/{tid}", btpT, expect=403)

# notation
n = call("POST", "/api/notations", cliT, {"projetId": pid, "notePourId": artId, "qualite": 5, "delai": 4, "communication": 5, "respectBudget": 4, "proprete": 5, "commentaire": "Tres bon travail"})
check(abs(n["scoreGlobal"] - 4.6) < 0.01, f"score pondere 4.6 (recu {n.get('scoreGlobal') if isinstance(n, dict) else n})")
call("POST", "/api/notations", cliT, {"projetId": pid, "notePourId": artId, "qualite": 5, "delai": 4, "communication": 5, "respectBudget": 4, "proprete": 5}, expect=409)
call("POST", "/api/notations", artT, {"projetId": pid, "notePourId": cliId, "qualite": 5, "delai": 5, "communication": 5, "respectBudget": 5, "proprete": 5})
call("POST", "/api/notations", btpT, {"projetId": pid, "notePourId": artId, "qualite": 1, "delai": 1, "communication": 1, "respectBudget": 1, "proprete": 1}, expect=403)
check(call("GET", f"/api/artisans/{artId}", cliT)["profileScore"] == 4.6, "profileScore mis a jour")

# retrait
call("POST", "/api/compte/retrait", artT, {"operateur": "AIRTEL_MONEY", "numero": "+22790000002", "montantFcfa": 999999999}, expect=400)
w = call("POST", "/api/compte/retrait", artT, {"operateur": "AIRTEL_MONEY", "numero": "+22790000002", "montantFcfa": 90000})
check(w["walletBalance"] == 1380000, f"retrait 90000 -> solde 1 380 000 ({w})")

# ---------------------------------------------------------------- litige
p2 = call("POST", "/api/clients/projets", cliT, {"titre": "Cloture", "typeProjet": "amenagement", "localisation": "Maradi",
                                                  "jalons": [{"libelle": "Mur", "montantFcfa": 200000}]})
call("POST", f"/api/clients/projets/{p2['id']}/publier", cliT)
t2 = call("POST", f"/api/clients/projets/{p2['id']}/engager", cliT, {"beneficiaireId": btpId})
call("POST", f"/api/escrow/{t2['id']}/litige", btpT, {"motif": "x"}, expect=409)  # pas finance
call("POST", f"/api/clients/escrow/{t2['id']}/payer", cliT, {"operateur": "NITA", "numero": "90000001"})
call("POST", f"/api/escrow/{t2['id']}/litige", artT, {"motif": "x"}, expect=403)
call("POST", f"/api/escrow/{t2['id']}/litige", btpT, {"motif": "Le client a change les plans"})
lit = call("GET", "/api/admin/litiges", admT)
check(len(lit) == 1, "1 litige ouvert")
call("PATCH", f"/api/admin/litiges/{t2['id']}/resoudre", admT, {"resolution": "COMPLETED"}, expect=400)
call("PATCH", f"/api/admin/litiges/{t2['id']}/resoudre", admT, {"resolution": "RESOLVED_CLIENT", "commentaire": "Travaux non demarres"})
check(call("GET", "/api/compte/moi", cliT)["walletBalance"] == 200000, "client rembourse 200000")
journal = call("GET", "/api/admin/journal", admT)
check(len(journal) >= 3, "journal admin")

# ---------------------------------------------------------------- AO ONG / BTP
call("POST", "/api/ong-institutions/appels-offres", ongT, {"titre": "Forage Tillaberi", "cahierDesChargesTexte": "Forage 80m", "dateLimiteDepot": "2099-12-31"})
ao2 = call("POST", "/api/ong-institutions/appels-offres", ongT, {"titre": "Ecole 3 classes", "cahierDesChargesTexte": "Construction ecole", "dateLimiteDepot": "2099-12-31"})
r = call("GET", "/api/entreprises-btp/appels-offres?q=forage", btpT)
check(len(r) == 1 and r[0]["titre"] == "Forage Tillaberi", "recherche AO par mot-cle")
cand = call("POST", f"/api/entreprises-btp/appels-offres/{ao2['id']}/candidatures", btpT, {"propositionTechnique": "Equipe de 12", "montantPropositionFcfa": 25000000})
call("POST", f"/api/entreprises-btp/appels-offres/{ao2['id']}/candidatures", btpT, {"propositionTechnique": "bis", "montantPropositionFcfa": 1}, expect=409)
call("POST", f"/api/entreprises-btp/appels-offres/{ao2['id']}/candidatures", artT, {"propositionTechnique": "x", "montantPropositionFcfa": 1}, expect=403)
check(len(call("GET", "/api/entreprises-btp/candidatures", btpT)) == 1, "mes candidatures BTP")
check(len(call("GET", "/api/ong-institutions/appels-offres", ongT)) == 2, "mes AO ONG")
cs = call("GET", f"/api/ong-institutions/appels-offres/{ao2['id']}/candidatures", ongT)
check(len(cs) == 1 and cs[0]["candidat"]["nomCommercial"] == "BTP Sahel", "candidature visible avec entreprise")
call("PATCH", f"/api/ong-institutions/candidatures/{cand['id']}/statut?statut=RETENUE", ongT)
check(call("GET", "/api/ong-institutions/appels-offres", ongT)[0]["statut"] == "ATTRIBUE", "AO attribue")

# ---------------------------------------------------------------- expert / propositions
p3 = call("POST", "/api/clients/projets", cliT, {"titre": "Immeuble R+2", "typeProjet": "construction", "localisation": "Zinder"})
call("POST", f"/api/clients/projets/{p3['id']}/publier", cliT)
prop = call("POST", "/api/experts/propositions", expT, {"projetId": p3["id"], "titre": "Etude structure", "description": "Etudes completes",
                                                        "phases": [{"libelle": "Esquisse", "montantFcfa": 300000}, {"libelle": "APD", "montantFcfa": 700000}]})
check(prop["montantTotalFcfa"] == 1000000, "total proposition 1 000 000")
call("POST", f"/api/experts/propositions/{prop['id']}/envoyer", expT)
recues = call("GET", "/api/clients/propositions", cliT)
check(len(recues) == 1, "proposition recue par le client")
call("PATCH", f"/api/clients/propositions/{prop['id']}/accepter", cliT)
check(call("GET", "/api/experts/propositions/mes-propositions", expT)[0]["statut"] == "ACCEPTEE", "proposition acceptee")

# ---------------------------------------------------------------- fournisseur
prod = call("POST", "/api/fournisseurs/catalogue", fouT, {"nom": "Ciment CPJ 45", "categorie": "ciment", "unite": "sac", "prixUnitaireFcfa": 6500, "stockDisponible": 300})
call("PUT", f"/api/fournisseurs/catalogue/{prod['id']}", fou2T, {"nom": "Vol", "prixUnitaireFcfa": 1}, expect=403)
call("PUT", f"/api/fournisseurs/catalogue/{prod['id']}", fouT, {"nom": "Ciment CPJ 45", "categorie": "ciment", "unite": "sac", "prixUnitaireFcfa": 6200, "stockDisponible": 250})
r = call("GET", "/api/fournisseurs/catalogue/recherche?q=ciment", cliT)
check(len(r) == 1 and r[0]["prixUnitaireFcfa"] == 6200, "recherche catalogue")
call("DELETE", f"/api/fournisseurs/catalogue/{prod['id']}", fou2T, expect=403)
call("DELETE", f"/api/fournisseurs/catalogue/{prod['id']}", fouT, expect=204)

# ---------------------------------------------------------------- messagerie, portfolio, notifications
m = call("POST", "/api/messagerie/messages", cliT, {"destinataireId": artId, "projetId": pid, "contenu": "Bonjour, merci pour le chantier"})
convs = call("GET", "/api/messagerie/conversations", artT)
check(len(convs) == 1, "conversation artisan")
call("POST", f"/api/messagerie/conversations/{convs[0]['id']}/messages", artT, {"contenu": "Avec plaisir !"})
msgs = call("GET", f"/api/messagerie/conversations/{convs[0]['id']}/messages", cliT)
check(len(msgs) == 2, "2 messages")
call("GET", f"/api/messagerie/conversations/{convs[0]['id']}/messages", btpT, expect=403)
pf = call("POST", "/api/portfolio", artT, {"titre": "Villa Plateau", "photoUrl": f["url"]})
check(len(call("GET", f"/api/portfolio/utilisateur/{artId}", cliT)) == 1, "portfolio public")
call("DELETE", f"/api/portfolio/{pf['id']}", cliT, expect=403)
notifs = call("GET", "/api/compte/notifications", artT)
check(len(notifs) >= 5, f"notifications artisan ({len(notifs)})")
call("POST", "/api/compte/notifications/lues", artT, expect=204)
check(call("GET", "/api/compte/moi", artT)["notificationsNonLues"] == 0, "notifications lues")
call("PATCH", "/api/compte/langue", artT, {"langue": "ha"})
call("PATCH", "/api/compte/langue", artT, {"langue": "en"}, expect=400)

# callback operateur (secret)
call("POST", "/api/public/paiements/callback", body={"reference": "x", "succes": True}, expect=403)
call("POST", "/api/public/paiements/callback", body={"reference": "inconnue", "succes": True}, expect=404,
     headers={"X-GJ-Signature": "CHANGE_ME_WEBHOOK"})

# ---------------------------------------------------------------- back-office : roles distincts
check(admR.get("adminRole") == "SUPER_ADMIN", "super-admin d'amorcage")
check(cliR.get("adminRole") is None and cliR["admin"] is False, "client non admin")
moi = call("GET", "/api/admin/moi", admT)
check(set(moi["permissions"]) == {"KYC_DECIDER", "LITIGES_TRANCHER", "FINANCES_EXPORTER", "EQUIPE_GERER"}, f"permissions super-admin {moi}")
equipe = call("GET", "/api/admin/equipe", admT)
check(len(equipe) == 1 and equipe[0]["amorcage"] and equipe[0]["aUnCompte"], "equipe initiale")
call("POST", "/api/admin/equipe", admT, {"phone": "90 00 00 00", "role": "LECTEUR"}, expect=409)
call("POST", "/api/admin/equipe", admT, {"phone": "abc", "role": "LECTEUR"}, expect=400)
call("POST", "/api/admin/equipe", admT, {"phone": "90000020", "nom": "Aicha KYC", "role": "OPERATEUR_KYC"})
call("POST", "/api/admin/equipe", admT, {"phone": "90000021", "nom": "Ibrahim Finances", "role": "GESTIONNAIRE_FINANCES"})
equipe = call("POST", "/api/admin/equipe", admT, {"phone": "90000022", "nom": "Lecteur", "role": "LECTEUR"})
check(len(equipe) == 4, "4 membres")
call("POST", "/api/admin/equipe", admT, {"phone": "90000022", "role": "LECTEUR"}, expect=409)
kycT, kycId, kycR = login("90000020", "CLIENT_PARTICULIER")
finT, finId, finR = login("90000021", "CLIENT_PARTICULIER")
lecT, lecId, lecR = login("90000022", "CLIENT_PARTICULIER")
check(kycR["adminRole"] == "OPERATEUR_KYC" and finR["adminRole"] == "GESTIONNAIRE_FINANCES" and lecR["adminRole"] == "LECTEUR", "roles a la connexion")
check(call("GET", "/api/compte/moi", finT)["adminRole"] == "GESTIONNAIRE_FINANCES", "role dans /compte/moi")
check(call("GET", "/api/admin/moi", lecT)["permissions"] == [], "lecteur sans permission")
# lecture pour tous les roles, refus pour un non-admin
for t in (kycT, finT, lecT):
    call("GET", "/api/admin/tableau-de-bord", t)
    call("GET", "/api/admin/projets", t)
    call("GET", "/api/admin/transactions", t)
    call("GET", "/api/admin/journal", t)
call("GET", "/api/admin/tableau-de-bord", cliT, expect=403)
call("GET", "/api/admin/equipe", cliT, expect=403)
# decisions reservees
call("PATCH", "/api/admin/kyc/inexistant/approuver", lecT, expect=403)
call("PATCH", "/api/admin/kyc/inexistant/approuver", finT, expect=403)
call("PATCH", "/api/admin/kyc/inexistant/approuver", kycT, expect=404)
call("PATCH", "/api/admin/litiges/inexistant/resoudre", kycT, {"resolution": "REFUNDED"}, expect=403)
call("PATCH", "/api/admin/litiges/inexistant/resoudre", lecT, {"resolution": "REFUNDED"}, expect=403)
call("GET", "/api/admin/transactions/export", lecT, expect=403)
call("POST", "/api/admin/equipe", finT, {"phone": "90000023", "role": "LECTEUR"}, expect=403)
membres = {m["phone"]: m for m in call("GET", "/api/admin/equipe", lecT)}
call("PATCH", f"/api/admin/equipe/{membres['+22790000022']['id']}", kycT, {"role": "SUPER_ADMIN"}, expect=403)

# tableau de bord : coherent avec le parcours ci-dessus
tdb = call("GET", "/api/admin/tableau-de-bord", admT)
check(tdb["utilisateurs"] >= 10 and tdb["nouveaux30j"] == tdb["utilisateurs"], f"inscrits {tdb['utilisateurs']}")
check(tdb["utilisateursParProfil"].get("ARTISAN", 0) >= 1, "repartition par profil")
check(tdb["retraitsFcfa"] == 90000, f"retraits {tdb['retraitsFcfa']}")
check(tdb["commissionsFcfa"] > 0, f"commissions {tdb['commissionsFcfa']}")
check(tdb["volumeSecuriseFcfa"] >= tdb["libereFcfa"] > 0, "volume >= libere")
check(len(tdb["serieMensuelle"]) == 12 and tdb["serieMensuelle"][-1]["inscriptions"] == tdb["utilisateurs"], "serie mensuelle")
check(tdb["serieMensuelle"][-1]["commissionsFcfa"] == tdb["commissionsFcfa"], "commissions du mois")
check(sum(s["nombre"] for s in tdb["sequestreParStatut"]) >= 1, "sequestre par statut")
check(tdb["projets"] >= 1 and tdb["projetsAvecPrestataire"] >= 1, "projets")

# projets et transactions
pr = call("GET", "/api/admin/projets?taille=5", finT)
check(pr["total"] == tdb["projets"] and len(pr["elements"]) <= 5, "pagination projets")
check(all(p["client"]["phone"].startswith("+227") for p in pr["elements"]), "telephone client visible des admins")
etats = {p["etat"] for p in call("GET", "/api/admin/projets?taille=100", finT)["elements"]}
check(len(etats) >= 1, f"etats projets {etats}")
term = call("GET", "/api/admin/projets?etat=TERMINE&taille=100", finT)
check(all(p["etat"] == "TERMINE" for p in term["elements"]), "filtre etat")
det = call("GET", f"/api/admin/projets/{pid}", lecT)
check(det["resume"]["id"] == pid and len(det["jalons"]) >= 1 and len(det["transactions"]) >= 1, "detail projet")
check(any(tr["libere"] for t in det["transactions"] for tr in t["tranches"]), "tranches liberees dans le detail")
call("GET", "/api/admin/projets/inexistant", lecT, expect=404)
tx = call("GET", "/api/admin/transactions", finT)
check(tx["total"] >= 1, "transactions")
check(call("GET", "/api/admin/transactions?statut=DISPUTED", finT)["total"] == tdb["litigesOuverts"], "filtre statut")
check(call("GET", "/api/admin/transactions?q=90%2000%2000%2001", finT)["total"] >= 1, "recherche par telephone")
check(call("GET", "/api/admin/transactions?q=zzzz-introuvable", finT)["total"] == 0, "recherche vide")
call("GET", "/api/admin/transactions?statut=INCONNU", finT, expect=400)
txd = call("GET", f"/api/admin/transactions/{tx['elements'][0]['id']}", lecT)
check(txd["transaction"]["id"] == tx["elements"][0]["id"], "detail transaction")
csv = call("GET", "/api/admin/transactions/export", finT)
check(isinstance(csv, str) and csv.startswith("\ufeffDate;Projet;") and csv.count("\n") == tx["total"] + 1, "export CSV")
rt = call("GET", "/api/admin/retraits", lecT)
check(rt["total"] == 1 and rt["elements"][0]["montantFcfa"] == 90000 and rt["elements"][0]["utilisateur"]["id"] == artId, "retraits")

# changement de role et retrait : effet immediat, sans reconnexion
call("PATCH", f"/api/admin/equipe/{membres['+22790000022']['id']}", admT, {"role": "OPERATEUR_KYC"})
check(call("GET", "/api/admin/moi", lecT)["role"] == "OPERATEUR_KYC", "promotion immediate")
call("PATCH", f"/api/admin/equipe/{membres['+22790000022']['id']}", admT, {"actif": False})
call("GET", "/api/admin/tableau-de-bord", lecT, expect=403)
call("PATCH", f"/api/admin/equipe/{membres['+22790000022']['id']}", admT, {"actif": True})
call("GET", "/api/admin/tableau-de-bord", lecT)
call("DELETE", f"/api/admin/equipe/{membres['+22790000022']['id']}", admT, expect=204)
call("GET", "/api/admin/tableau-de-bord", lecT, expect=403)
check(call("GET", "/api/compte/moi", lecT)["admin"] is False, "plus admin apres retrait")
actions = [a["action"] for a in call("GET", "/api/admin/journal", admT)]
check({"ADMIN_AJOUTE", "ADMIN_MODIFIE", "ADMIN_RETIRE"} <= set(actions), "journal de l'equipe")

print(f"OK: {ok_count}  ECHECS: {len(fails)}")
for x in fails:
    print(" -", x)
sys.exit(1 if fails else 0)

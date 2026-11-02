package com.gidajari.service;

import com.gidajari.dto.AdminVues.*;
import com.gidajari.entity.*;
import com.gidajari.entity.enums.EscrowStatus;
import com.gidajari.entity.enums.JalonStatus;
import com.gidajari.repository.*;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pilotage du back-office : indicateurs du tableau de bord, suivi des projets,
 * des transactions de sequestre et des retraits (lecture seule).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminPilotageService {

    /** Fuseau du Niger, pour regrouper les montants par mois civil local. */
    static final ZoneId FUSEAU = ZoneId.of("Africa/Niamey");
    private static final DateTimeFormatter MOIS = DateTimeFormatter.ofPattern("yyyy-MM");

    /** Statuts ou l'argent a effectivement ete verse sur le sequestre. */
    static final Set<EscrowStatus> PAYES = EnumSet.of(EscrowStatus.FUNDED, EscrowStatus.PARTIAL_RELEASE,
            EscrowStatus.COMPLETED, EscrowStatus.DISPUTED, EscrowStatus.RESOLVED_CLIENT,
            EscrowStatus.RESOLVED_PROVIDER, EscrowStatus.REFUNDED);
    /** Statuts ou une partie des fonds est encore bloquee sur le sequestre. */
    static final Set<EscrowStatus> BLOQUES = EnumSet.of(EscrowStatus.FUNDED, EscrowStatus.PARTIAL_RELEASE,
            EscrowStatus.DISPUTED);

    private final UserRepository userRepository;
    private final ProjetRepository projetRepository;
    private final JalonRepository jalonRepository;
    private final EscrowTransactionRepository escrowRepository;
    private final EscrowMilestoneRepository milestoneRepository;
    private final RetraitCompteRepository retraitRepository;
    private final KYCSubmissionRepository kycRepository;
    private final AppelOffreRepository appelOffreRepository;
    private final CatalogProduitRepository catalogRepository;

    // ------------------------------------------------------------------ tableau de bord

    public TableauDeBord tableauDeBord() {
        Instant maintenant = Instant.now();

        Map<String, Long> parProfil = new LinkedHashMap<>();
        for (Object[] r : userRepository.compterParProfil()) {
            parProfil.put(r[0] == null ? "SANS_PROFIL" : r[0].toString(), (Long) r[1]);
        }
        Map<String, Long> parKyc = new TreeMap<>();
        for (Object[] r : userRepository.compterParKyc()) {
            parKyc.put(r[0].toString(), (Long) r[1]);
        }
        long utilisateurs = parProfil.values().stream().mapToLong(Long::longValue).sum();

        List<StatutSequestre> parStatut = new ArrayList<>();
        long volume = 0, bloque = 0, libere = 0, litiges = 0;
        Map<EscrowStatus, Object[]> agregats = new EnumMap<>(EscrowStatus.class);
        for (Object[] r : escrowRepository.agregerParStatut()) {
            agregats.put((EscrowStatus) r[0], r);
        }
        for (EscrowStatus s : EscrowStatus.values()) {
            Object[] r = agregats.get(s);
            long nombre = r == null ? 0 : (Long) r[1];
            long total = r == null ? 0 : ((Number) r[2]).longValue();
            long dejaLibere = r == null ? 0 : ((Number) r[3]).longValue();
            parStatut.add(new StatutSequestre(s.name(), nombre, total));
            if (PAYES.contains(s)) {
                volume += total;
            }
            if (BLOQUES.contains(s)) {
                bloque += total - dejaLibere;
            }
            libere += dejaLibere;
            if (s == EscrowStatus.DISPUTED) {
                litiges = nombre;
            }
        }

        long kycEnAttente = kycRepository.countByStatut("EN_ATTENTE");
        long jalonsEnAttente = jalonRepository.countByStatut(JalonStatus.SOUMIS);
        long jalonsBientotAuto = jalonRepository.countByStatutAndSoumisLeBefore(JalonStatus.SOUMIS,
                maintenant.minus(36, ChronoUnit.HOURS));
        long paiementsBloques = escrowRepository.countByStatutAndCreatedAtBefore(EscrowStatus.PENDING_PAYMENT,
                maintenant.minus(1, ChronoUnit.HOURS));

        List<Alerte> alertes = new ArrayList<>();
        if (litiges > 0) {
            alertes.add(new Alerte("danger", litiges + " litige(s) à trancher", "/admin/litiges"));
        }
        if (paiementsBloques > 0) {
            alertes.add(new Alerte("warning", paiementsBloques
                    + " paiement(s) Mobile Money non confirmé(s) depuis plus d'une heure",
                    "/admin/transactions?statut=PENDING_PAYMENT"));
        }
        if (kycEnAttente > 0) {
            alertes.add(new Alerte("warning", kycEnAttente + " document(s) KYC en attente de validation", "/admin/kyc"));
        }
        if (jalonsBientotAuto > 0) {
            alertes.add(new Alerte("info", jalonsBientotAuto
                    + " jalon(s) soumis depuis plus de 36 h : auto-validation et libération des fonds dans moins de 12 h",
                    "/admin/projets?etat=EN_COURS"));
        }

        return new TableauDeBord(
                utilisateurs, userRepository.countByCreatedAtAfter(maintenant.minus(30, ChronoUnit.DAYS)),
                userRepository.countByActifFalse(), parProfil, parKyc,
                projetRepository.count(), projetRepository.countByPublieTrue(),
                escrowRepository.compterProjetsAvecPrestataire(),
                appelOffreRepository.count(), catalogRepository.count(),
                volume, bloque, libere, milestoneRepository.totalCommissions(),
                retraitRepository.totalMontant(), litiges, kycEnAttente, jalonsEnAttente,
                parStatut, serieMensuelle(12), alertes, maintenant);
    }

    /** Inscriptions, volume securise, commissions et retraits des n derniers mois (mois courant inclus). */
    List<PointMensuel> serieMensuelle(int nbMois) {
        YearMonth courant = YearMonth.now(FUSEAU);
        YearMonth premier = courant.minusMonths(nbMois - 1L);
        Instant depuis = premier.atDay(1).atStartOfDay(FUSEAU).toInstant();

        Map<String, long[]> parMois = new LinkedHashMap<>();
        for (int i = 0; i < nbMois; i++) {
            parMois.put(premier.plusMonths(i).format(MOIS), new long[4]);
        }
        for (Instant d : userRepository.datesInscriptionDepuis(depuis)) {
            cumuler(parMois, d, 0, 1);
        }
        for (Object[] r : escrowRepository.montantsDepuis(depuis, PAYES)) {
            cumuler(parMois, (Instant) r[0], 1, montant(r[1]));
        }
        for (Object[] r : milestoneRepository.commissionsDepuis(depuis)) {
            cumuler(parMois, (Instant) r[0], 2, montant(r[1]));
        }
        for (Object[] r : retraitRepository.montantsDepuis(depuis)) {
            cumuler(parMois, (Instant) r[0], 3, montant(r[1]));
        }
        return parMois.entrySet().stream()
                .map(e -> new PointMensuel(e.getKey(), e.getValue()[0], e.getValue()[1], e.getValue()[2], e.getValue()[3]))
                .toList();
    }

    private static void cumuler(Map<String, long[]> parMois, Instant date, int indice, long valeur) {
        if (date == null) {
            return;
        }
        long[] ligne = parMois.get(date.atZone(FUSEAU).format(MOIS));
        if (ligne != null) {
            ligne[indice] += valeur;
        }
    }

    private static long montant(Object o) {
        return o == null ? 0 : ((Number) o).longValue();
    }

    // ------------------------------------------------------------------ projets

    public Page<ProjetLigne> projets(String q, EtatProjet etat, int page, int taille) {
        List<Projet> projets = projetRepository.rechercherAdmin(motif(q));
        Map<String, EscrowTransaction> dernieres = derniereTransactionParProjet();
        List<ProjetLigne> lignes = projets.stream()
                .map(p -> ligne(p, dernieres.get(p.getId())))
                .filter(l -> etat == null || l.etat() == etat)
                .toList();
        return paginer(lignes, page, taille);
    }

    public ProjetDetail projet(String id) {
        Projet p = projetRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Projet introuvable : " + id));
        List<EscrowTransaction> transactions = escrowRepository.findByProjetIdOrderByCreatedAtDesc(id);
        List<Jalon> jalons = jalonRepository.findByProjetIdOrderByOrdreAsc(id);
        return new ProjetDetail(ligne(p, transactions.isEmpty() ? null : transactions.get(0)),
                p.getDescription(), jalons,
                transactions.stream().map(this::detail).toList());
    }

    private Map<String, EscrowTransaction> derniereTransactionParProjet() {
        return escrowRepository.findAll().stream()
                .collect(Collectors.toMap(t -> t.getProjet().getId(), Function.identity(),
                        (a, b) -> a.getCreatedAt().isAfter(b.getCreatedAt()) ? a : b));
    }

    ProjetLigne ligne(Projet p, EscrowTransaction t) {
        List<Jalon> jalons = p.getJalons();
        int valides = (int) jalons.stream().filter(j -> j.getStatut() == JalonStatus.VALIDE).count();
        return new ProjetLigne(p.getId(), p.getTitre(), p.getLocalisation(), p.getTypeProjet(),
                p.getBudgetEstimeFcfa(), p.getCreatedAt(), etat(p, t),
                Personne.de(p.getClient()), t == null ? null : Personne.de(t.getBeneficiaire()),
                jalons.size(), valides,
                t == null ? null : t.getMontantTotalFcfa(), t == null ? null : t.getMontantLibereFcfa(),
                t == null ? null : t.getId(), t == null ? null : t.getStatut().name());
    }

    static EtatProjet etat(Projet p, EscrowTransaction t) {
        if (t == null) {
            return p.isPublie() ? EtatProjet.PUBLIE : EtatProjet.BROUILLON;
        }
        return switch (t.getStatut()) {
            case DRAFT, PENDING_PAYMENT -> EtatProjet.ATTENTE_PAIEMENT;
            case FUNDED, PARTIAL_RELEASE -> EtatProjet.EN_COURS;
            case DISPUTED -> EtatProjet.LITIGE;
            case COMPLETED, RESOLVED_CLIENT, RESOLVED_PROVIDER, REFUNDED -> EtatProjet.TERMINE;
        };
    }

    // ------------------------------------------------------------------ transactions et retraits

    public Page<TransactionLigne> transactions(String q, EscrowStatus statut, int page, int taille) {
        var resultat = escrowRepository.rechercher(motif(q), statuts(statut), PageRequest.of(page, borner(taille)));
        return new Page<>(resultat.getContent().stream().map(this::ligne).toList(),
                resultat.getTotalElements(), page, borner(taille));
    }

    /** Toutes les transactions correspondant aux filtres (export CSV). */
    public List<TransactionLigne> toutesTransactions(String q, EscrowStatus statut) {
        return escrowRepository.rechercher(motif(q), statuts(statut), PageRequest.of(0, 10_000))
                .getContent().stream().map(this::ligne).toList();
    }

    public TransactionDetail transaction(String id) {
        return detail(escrowRepository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Transaction introuvable : " + id)));
    }

    public Page<RetraitLigne> retraits(String q, int page, int taille) {
        var resultat = retraitRepository.rechercher(motif(q), PageRequest.of(page, borner(taille)));
        return new Page<>(resultat.getContent().stream()
                .map(r -> new RetraitLigne(r.getId(), Personne.de(r.getUser()), r.getOperateur().name(), r.getNumero(),
                        r.getMontantFcfa(), r.getReference(), r.getCreatedAt()))
                .toList(), resultat.getTotalElements(), page, borner(taille));
    }

    TransactionLigne ligne(EscrowTransaction t) {
        long commissions = t.getMilestones().stream()
                .filter(EscrowMilestone::isLibere)
                .mapToLong(m -> m.getCommissionFcfa() == null ? 0 : m.getCommissionFcfa())
                .sum();
        return new TransactionLigne(t.getId(), t.getProjet().getId(), t.getProjet().getTitre(),
                Personne.de(t.getPayeur()), Personne.de(t.getBeneficiaire()),
                t.getMontantTotalFcfa(), t.getMontantLibereFcfa(), commissions,
                t.getNiveauCommission() != null ? t.getNiveauCommission().name() : null,
                t.getStatut().name(), t.getOperateurMobileMoney(), t.getNumeroPayeur(), t.getReferencePaiement(),
                t.getMotifLitige(), t.getCreatedAt(), t.getUpdatedAt());
    }

    private TransactionDetail detail(EscrowTransaction t) {
        List<TrancheLigne> tranches = t.getMilestones().stream()
                .sorted(Comparator.comparing(m -> m.getJalon() != null && m.getJalon().getOrdre() != null
                        ? m.getJalon().getOrdre() : Integer.MAX_VALUE))
                .map(m -> new TrancheLigne(m.getId(), m.getJalon() != null ? m.getJalon().getId() : null,
                        m.getJalon() != null ? m.getJalon().getLibelle() : null, m.getMontantFcfa(),
                        m.getCommissionFcfa(), m.getMontantNetFcfa(), m.isLibere(), m.getLibereLe()))
                .toList();
        return new TransactionDetail(ligne(t), tranches);
    }

    // ------------------------------------------------------------------ utilitaires

    private static Collection<EscrowStatus> statuts(EscrowStatus statut) {
        return statut == null ? EnumSet.allOf(EscrowStatus.class) : EnumSet.of(statut);
    }

    /** Motif LIKE en minuscules ; "%" quand aucun filtre n'est saisi. */
    static String motif(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String nettoye = q.trim().toLowerCase(Locale.ROOT).replace("%", "").replace("_", "");
        // Un numero saisi sans indicatif (90 00 00 01) doit retrouver +22790000001
        String chiffres = nettoye.replaceAll("[\\s.\\-]", "");
        if (chiffres.matches("\\d{4,}")) {
            nettoye = chiffres;
        }
        return "%" + nettoye + "%";
    }

    private static int borner(int taille) {
        return Math.max(1, Math.min(taille, 100));
    }

    private static <T> Page<T> paginer(List<T> lignes, int page, int taille) {
        int t = borner(taille);
        int debut = Math.min(Math.max(page, 0) * t, lignes.size());
        int fin = Math.min(debut + t, lignes.size());
        return new Page<>(lignes.subList(debut, fin), lignes.size(), page, t);
    }
}

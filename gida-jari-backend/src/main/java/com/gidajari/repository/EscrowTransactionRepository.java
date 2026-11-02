package com.gidajari.repository;

import com.gidajari.entity.EscrowTransaction;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.Collection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EscrowTransactionRepository extends JpaRepository<EscrowTransaction, String> {
    List<EscrowTransaction> findByProjetId(String projetId);
    List<EscrowTransaction> findByProjetIdOrderByCreatedAtDesc(String projetId);
    List<EscrowTransaction> findByBeneficiaireIdOrderByCreatedAtDesc(String beneficiaireId);
    List<EscrowTransaction> findByStatutOrderByUpdatedAtAsc(com.gidajari.entity.enums.EscrowStatus statut);
    Optional<EscrowTransaction> findByReferencePaiement(String referencePaiement);

    // --- Back-office
    @Query("select t.statut, count(t), coalesce(sum(t.montantTotalFcfa), 0), coalesce(sum(t.montantLibereFcfa), 0) "
            + "from EscrowTransaction t group by t.statut")
    List<Object[]> agregerParStatut();

    @Query("select t.createdAt, t.montantTotalFcfa from EscrowTransaction t "
            + "where t.createdAt >= :depuis and t.statut in :statuts")
    List<Object[]> montantsDepuis(@Param("depuis") Instant depuis,
                                  @Param("statuts") Collection<com.gidajari.entity.enums.EscrowStatus> statuts);

    @Query("select count(distinct t.projet.id) from EscrowTransaction t")
    long compterProjetsAvecPrestataire();

    long countByStatutAndCreatedAtBefore(com.gidajari.entity.enums.EscrowStatus statut, Instant date);

    /** q = motif LIKE en minuscules ("%" = tout) ; statuts = statuts retenus (tous si aucun filtre). */
    @Query(value = "select t from EscrowTransaction t join t.projet p join t.payeur pa join t.beneficiaire b "
            + "where t.statut in :statuts and (lower(p.titre) like :q or pa.phone like :q or b.phone like :q "
            + "or lower(coalesce(t.referencePaiement, '')) like :q) order by t.createdAt desc",
            countQuery = "select count(t) from EscrowTransaction t join t.projet p join t.payeur pa join t.beneficiaire b "
            + "where t.statut in :statuts and (lower(p.titre) like :q or pa.phone like :q or b.phone like :q "
            + "or lower(coalesce(t.referencePaiement, '')) like :q)")
    Page<EscrowTransaction> rechercher(@Param("q") String q,
                                       @Param("statuts") Collection<com.gidajari.entity.enums.EscrowStatus> statuts,
                                       Pageable pageable);
}

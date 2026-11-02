package com.gidajari.repository;

import com.gidajari.entity.EscrowMilestone;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EscrowMilestoneRepository extends JpaRepository<EscrowMilestone, String> {
    Optional<EscrowMilestone> findByJalonId(String jalonId);

    @Query("select coalesce(sum(m.commissionFcfa), 0) from EscrowMilestone m where m.libere = true")
    long totalCommissions();

    @Query("select m.libereLe, m.commissionFcfa from EscrowMilestone m where m.libere = true and m.libereLe >= :depuis")
    List<Object[]> commissionsDepuis(@Param("depuis") Instant depuis);
}

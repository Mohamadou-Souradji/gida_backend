package com.gidajari.service;

import com.gidajari.entity.Jalon;
import com.gidajari.entity.enums.JalonStatus;
import com.gidajari.repository.JalonRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Tache planifiee (@Scheduled) - auto-validation des jalons.
 *
 * Regle de gestion n3 (Cahier des Charges Technique) : tout jalon soumis
 * par un prestataire et restant sans action du client pendant 48h est
 * automatiquement valide, et la tranche de sequestre correspondante liberee.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JalonAutoValidationScheduler {

    private final JalonRepository jalonRepository;
    private final EscrowService escrowService;

    @Value("${gida-jari.jalon.auto-validation-heures:48}")
    private int delaiHeures;

    /** Execution toutes les heures - verifie les jalons soumis depuis plus de 48h. */
    @Scheduled(cron = "0 0 * * * *")
    public void autoValiderJalonsExpires() {
        Instant seuil = Instant.now().minus(delaiHeures, ChronoUnit.HOURS);
        List<Jalon> jalonsAValider = jalonRepository.findByStatutAndSoumisLeBefore(JalonStatus.SOUMIS, seuil);

        for (Jalon jalon : jalonsAValider) {
            try {
                // Une transaction par jalon : un echec n'empeche pas les autres validations.
                escrowService.autoValider(jalon);
            } catch (Exception e) {
                log.error("Auto-validation du jalon {} impossible : {}", jalon.getId(), e.getMessage());
            }
        }
    }
}

package com.gidajari.service;

import com.gidajari.entity.RetraitCompte;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.KycLevel;
import com.gidajari.entity.enums.OperateurMobileMoney;
import com.gidajari.integration.MobileMoneyGateway;
import com.gidajari.repository.RetraitCompteRepository;
import com.gidajari.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Solde Gida Jari (walletBalance) : credite par les liberations de sequestre
 * (prestataire) et les remboursements (client), puis retire vers Mobile Money.
 */
@Service
@RequiredArgsConstructor
public class CompteService {

    private final UserRepository userRepository;
    private final RetraitCompteRepository retraitRepository;
    private final MobileMoneyGateway mobileMoneyGateway;
    private final KYCService kycService;
    private final NotificationService notificationService;

    @Transactional
    public User retirer(User user, OperateurMobileMoney operateur, String numero, long montant) {
        kycService.exigerNiveau(user, KycLevel.NIVEAU_2_IDENTITE_DECLAREE);
        if (montant <= 0 || montant > user.getWalletBalance()) {
            throw new IllegalArgumentException("Montant invalide : solde disponible " + user.getWalletBalance() + " FCFA");
        }
        MobileMoneyGateway.Resultat res = mobileMoneyGateway.verser(operateur, numero, montant, "Retrait solde Gida Jari");
        user.setWalletBalance(user.getWalletBalance() - montant);
        userRepository.save(user);
        RetraitCompte retrait = new RetraitCompte();
        retrait.setUser(user);
        retrait.setOperateur(operateur);
        retrait.setNumero(numero);
        retrait.setMontantFcfa(montant);
        retrait.setReference(res.reference());
        retraitRepository.save(retrait);
        notificationService.notifier(user, "Retrait", montant + " FCFA envoyés vers " + numero
                + " (" + operateur + ", ref " + res.reference() + ").");
        return user;
    }

    @Transactional
    public User changerLangue(User user, String langue) {
        if (!"fr".equals(langue) && !"ha".equals(langue)) {
            throw new IllegalArgumentException("Langue supportee : fr ou ha");
        }
        user.setLanguePreferee(langue);
        return userRepository.save(user);
    }
}

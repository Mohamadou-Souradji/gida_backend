package com.gidajari.service;

import com.gidajari.dto.AdminVues;
import com.gidajari.dto.AdminVues.MembreEquipe;
import com.gidajari.entity.AdminMembre;
import com.gidajari.entity.User;
import com.gidajari.entity.enums.AdminRole;
import com.gidajari.repository.AdminMembreRepository;
import com.gidajari.repository.UserRepository;
import com.gidajari.service.exception.RessourceIntrouvableException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Equipe d'administration a roles distincts.
 *
 * Amorcage : les numeros de gida-jari.admin.telephones (GJ_ADMIN_PHONES) sont super-admins
 * d'office et ne peuvent pas etre modifies depuis l'interface (garantit qu'on ne perd jamais
 * l'acces au back-office). Les autres membres sont geres par un super-admin.
 */
@Service
@RequiredArgsConstructor
public class AdminEquipeService {

    private final AdminMembreRepository repository;
    private final UserRepository userRepository;
    private final AdminJournalService journal;

    @Value("${gida-jari.admin.telephones:}")
    private String telephonesAmorcage;

    public Set<String> telephonesAmorcage() {
        return Arrays.stream(telephonesAmorcage.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(OTPService::normaliserTelephone)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Role d'administration associe a un numero (vide si ce n'est pas un admin actif). */
    @Transactional(readOnly = true)
    public Optional<AdminRole> roleDuTelephone(String phone) {
        if (phone == null) {
            return Optional.empty();
        }
        if (telephonesAmorcage().contains(phone)) {
            return Optional.of(AdminRole.SUPER_ADMIN);
        }
        return repository.findByPhone(phone).filter(AdminMembre::isActif).map(AdminMembre::getRole);
    }

    @Transactional(readOnly = true)
    public Optional<AdminRole> roleDeUtilisateur(String userId) {
        return userRepository.findById(userId).filter(User::isActif).flatMap(u -> roleDuTelephone(u.getPhone()));
    }

    @Transactional(readOnly = true)
    public List<MembreEquipe> lister() {
        List<MembreEquipe> liste = new ArrayList<>();
        for (String phone : telephonesAmorcage()) {
            Optional<User> u = userRepository.findByPhone(phone);
            String nom = u.map(AdminVues::nom).filter(n -> !n.startsWith("(")).orElse("Super-admin (configuration serveur)");
            liste.add(new MembreEquipe(null, phone, nom,
                    AdminRole.SUPER_ADMIN.name(), true, true, u.isPresent(), null));
        }
        for (AdminMembre m : repository.findAllByOrderByCreatedAtAsc()) {
            if (telephonesAmorcage().contains(m.getPhone())) {
                continue;
            }
            boolean compte = userRepository.existsByPhone(m.getPhone());
            liste.add(new MembreEquipe(m.getId(), m.getPhone(), m.getNom(), m.getRole().name(), m.isActif(),
                    false, compte, m.getCreatedAt()));
        }
        return liste;
    }

    @Transactional
    public AdminMembre ajouter(String telephone, String nom, AdminRole role, User auteur) {
        String phone = OTPService.normaliserTelephone(telephone);
        if (role == null) {
            throw new IllegalArgumentException("Choisissez un rôle");
        }
        if (telephonesAmorcage().contains(phone)) {
            throw new IllegalStateException("Ce numéro est déjà super-admin par la configuration du serveur");
        }
        if (repository.findByPhone(phone).isPresent()) {
            throw new IllegalStateException("Ce numéro fait déjà partie de l'équipe");
        }
        AdminMembre m = new AdminMembre();
        m.setPhone(phone);
        m.setNom(nom == null || nom.isBlank() ? null : nom.trim());
        m.setRole(role);
        m.setAjoutePar(auteur);
        repository.save(m);
        journal.journaliser(auteur, "ADMIN_AJOUTE", "AdminMembre", m.getId(), phone + " ajouté comme " + role);
        return m;
    }

    @Transactional
    public AdminMembre modifier(String id, AdminRole role, Boolean actif, String nom, User auteur) {
        AdminMembre m = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Membre introuvable : " + id));
        if (m.getPhone().equals(auteur.getPhone())) {
            throw new IllegalStateException("Vous ne pouvez pas modifier votre propre rôle");
        }
        StringBuilder detail = new StringBuilder(m.getPhone());
        if (role != null && role != m.getRole()) {
            detail.append(" : rôle ").append(m.getRole()).append(" -> ").append(role);
            m.setRole(role);
        }
        if (actif != null && actif != m.isActif()) {
            detail.append(actif ? " : réactivé" : " : désactivé");
            m.setActif(actif);
        }
        if (nom != null) {
            m.setNom(nom.isBlank() ? null : nom.trim());
        }
        repository.save(m);
        journal.journaliser(auteur, "ADMIN_MODIFIE", "AdminMembre", m.getId(), detail.toString());
        return m;
    }

    @Transactional
    public void retirer(String id, User auteur) {
        AdminMembre m = repository.findById(id)
                .orElseThrow(() -> new RessourceIntrouvableException("Membre introuvable : " + id));
        if (m.getPhone().equals(auteur.getPhone())) {
            throw new IllegalStateException("Vous ne pouvez pas vous retirer vous-même de l'équipe");
        }
        repository.delete(m);
        journal.journaliser(auteur, "ADMIN_RETIRE", "AdminMembre", id, m.getPhone() + " (" + m.getRole() + ") retiré de l'équipe");
    }
}

package com.gidajari.repository;

import com.gidajari.entity.Notation;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface NotationRepository extends JpaRepository<Notation, String> {
    List<Notation> findByNotePourIdOrderByCreatedAtDesc(String notePourId);
    boolean existsByProjetIdAndNoteParIdAndNotePourId(String projetId, String noteParId, String notePourId);
}

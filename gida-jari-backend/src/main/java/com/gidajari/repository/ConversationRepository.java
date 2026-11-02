package com.gidajari.repository;

import com.gidajari.entity.Conversation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {
    @Query("select c from Conversation c where c.participantA.id = :userId or c.participantB.id = :userId order by c.createdAt desc")
    List<Conversation> findByParticipant(@Param("userId") String userId);

    @Query("select c from Conversation c where (c.participantA.id = :a and c.participantB.id = :b) or (c.participantA.id = :b and c.participantB.id = :a)")
    List<Conversation> findEntre(@Param("a") String a, @Param("b") String b);
}

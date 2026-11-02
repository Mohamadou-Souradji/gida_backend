package com.gidajari.repository;

import com.gidajari.entity.Message;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MessageRepository extends JpaRepository<Message, String> {
    List<Message> findByConversationIdOrderByCreatedAtAsc(String conversationId);
    long countByConversationIdAndLuFalseAndExpediteurIdNot(String conversationId, String expediteurId);
}

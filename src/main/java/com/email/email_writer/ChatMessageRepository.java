package com.email.email_writer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    @Query(value = "SELECT * FROM chat_messages WHERE embedding IS NOT NULL ORDER BY embedding <-> CAST(:queryEmbedding AS vector) LIMIT 3",
            nativeQuery = true)
    List<ChatMessage> findSimilarMessages(@Param("queryEmbedding") String queryEmbedding);

    @Query("SELECT c FROM ChatMessage c ORDER BY c.timestamp DESC")
    List<ChatMessage> findRecentMessages();

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO chat_messages (user_message, bot_response, embedding, timestamp) VALUES (:userMessage, :botResponse, CAST(:embedding AS vector), NOW())",
            nativeQuery = true)
    void saveWithEmbedding(@Param("userMessage") String userMessage,
                           @Param("botResponse") String botResponse,
                           @Param("embedding") String embedding);
}
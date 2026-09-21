package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.ChatMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The messages of a chat session, in turn order; a session's sandbox was checked when the session was read. */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySessionIdOrderByTurnAscRoleDesc(UUID sessionId);
}

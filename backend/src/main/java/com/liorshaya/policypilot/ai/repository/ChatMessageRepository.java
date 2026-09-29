package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.ChatMessageEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The messages of a chat session, in turn order; a session's sandbox was checked when the session was read. */
public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, UUID> {

    List<ChatMessageEntity> findBySessionIdOrderByTurnAscRoleDesc(UUID sessionId);

    /** The messages of several sessions of one sandbox, each session's in turn order, for the list of conversations. */
    List<ChatMessageEntity> findBySessionIdInOrderBySessionIdAscTurnAscRoleDesc(Collection<UUID> sessionIds);
}

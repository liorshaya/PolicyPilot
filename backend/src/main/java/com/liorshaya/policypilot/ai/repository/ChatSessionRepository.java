package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.ChatSessionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Chat sessions, always looked up with the caller's sandbox: another sandbox's session reads as absent. */
public interface ChatSessionRepository extends JpaRepository<ChatSessionEntity, UUID> {

    Optional<ChatSessionEntity> findByIdAndSandboxId(UUID id, UUID sandboxId);
}

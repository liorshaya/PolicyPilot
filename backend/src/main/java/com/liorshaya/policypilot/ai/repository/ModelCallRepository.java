package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.ModelCallEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The ledger of model calls; it is only ever appended to (Document 4, Logging for every call). */
public interface ModelCallRepository extends JpaRepository<ModelCallEntity, UUID> {

    /** The calls of one prompt version, oldest first: what the cost view and the evaluation runner read. */
    List<ModelCallEntity> findByPromptNameAndPromptVersionOrderByAtAsc(String promptName, String promptVersion);
}

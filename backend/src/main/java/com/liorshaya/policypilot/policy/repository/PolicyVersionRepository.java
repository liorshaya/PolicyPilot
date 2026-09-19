package com.liorshaya.policypilot.policy.repository;

import com.liorshaya.policypilot.policy.entity.PolicyVersionEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Policy versions by id or by document and number; the caller has already checked the document's sandbox. */
public interface PolicyVersionRepository extends JpaRepository<PolicyVersionEntity, UUID> {

    @Query("select v from PolicyVersionEntity v where v.document.id = :documentId and v.versionNo = :versionNo")
    Optional<PolicyVersionEntity> findByDocument(@Param("documentId") UUID documentId, @Param("versionNo") int versionNo);
}

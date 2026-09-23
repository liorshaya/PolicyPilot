package com.liorshaya.policypilot.change.repository;

import com.liorshaya.policypilot.change.entity.ChangeRequestEntity;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/**
 * The stored change requests (Document 2, change_request). There is no delete: the full proposal is kept even when it
 * is rejected, and the database refuses the API's role a delete as well.
 */
public interface ChangeRequestRepository extends Repository<ChangeRequestEntity, UUID> {

    ChangeRequestEntity save(ChangeRequestEntity request);
}

package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.CachedResponseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Cached model answers by their input hash (Document 2, Cached demo outputs). */
public interface CachedResponseRepository extends JpaRepository<CachedResponseEntity, String> {}

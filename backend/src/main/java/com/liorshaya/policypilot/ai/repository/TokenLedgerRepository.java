package com.liorshaya.policypilot.ai.repository;

import com.liorshaya.policypilot.ai.entity.TokenLedgerDayEntity;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** The daily token ledger (Document 5, Spend caps). */
public interface TokenLedgerRepository extends JpaRepository<TokenLedgerDayEntity, LocalDate> {

    /**
     * The day's row, locked for the update that follows, so two requests cannot both spend the last tokens of
     * the budget.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from TokenLedgerDayEntity d where d.day = :day")
    Optional<TokenLedgerDayEntity> findForUpdate(LocalDate day);
}

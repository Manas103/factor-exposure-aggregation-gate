package com.manas.fxexp.repo;

import com.manas.fxexp.domain.Trade;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TradeRepository extends JpaRepository<Trade, Long> {

    Optional<Trade> findByExternalId(String externalId);

    List<Trade> findByStrategyId(String strategyId);

    @Query("SELECT t FROM Trade t, Strategy s WHERE s.id = t.strategyId AND s.fundId = :fundId")
    List<Trade> findByFundId(@Param("fundId") String fundId);
}

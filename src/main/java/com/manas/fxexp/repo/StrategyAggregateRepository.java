package com.manas.fxexp.repo;

import com.manas.fxexp.domain.StrategyAggregate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StrategyAggregateRepository extends JpaRepository<StrategyAggregate, String> {

    Optional<StrategyAggregate> findByStrategyId(String strategyId);

    List<StrategyAggregate> findByFundId(String fundId);
}

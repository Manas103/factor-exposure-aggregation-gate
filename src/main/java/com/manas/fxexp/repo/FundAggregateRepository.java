package com.manas.fxexp.repo;

import com.manas.fxexp.domain.FundAggregate;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FundAggregateRepository extends JpaRepository<FundAggregate, String> {
}

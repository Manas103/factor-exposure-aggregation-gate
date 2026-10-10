package com.manas.fxexp.repo;

import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.RiskLimit;
import com.manas.fxexp.domain.ScopeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskLimitRepository extends JpaRepository<RiskLimit, Long> {

    List<RiskLimit> findByScopeTypeAndScopeId(ScopeType scopeType, String scopeId);

    List<RiskLimit> findByScopeTypeAndScopeIdAndFactor(ScopeType scopeType, String scopeId, FactorType factor);
}

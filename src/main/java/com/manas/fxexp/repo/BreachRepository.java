package com.manas.fxexp.repo;

import com.manas.fxexp.domain.Breach;
import com.manas.fxexp.domain.FactorType;
import com.manas.fxexp.domain.ScopeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreachRepository extends JpaRepository<Breach, Long> {

    Optional<Breach> findByScopeTypeAndScopeIdAndFactorAndActiveTrue(ScopeType scopeType, String scopeId,
                                                                      FactorType factor);

    List<Breach> findByScopeTypeAndScopeIdAndActiveTrue(ScopeType scopeType, String scopeId);

    List<Breach> findByActiveTrue();
}

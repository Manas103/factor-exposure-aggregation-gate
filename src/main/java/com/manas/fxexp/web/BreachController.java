package com.manas.fxexp.web;

import com.manas.fxexp.repo.BreachRepository;
import com.manas.fxexp.web.dto.BreachView;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BreachController {

    private final BreachRepository breachRepository;

    public BreachController(BreachRepository breachRepository) {
        this.breachRepository = breachRepository;
    }

    @GetMapping("/breaches")
    public List<BreachView> listActiveBreaches() {
        return breachRepository.findByActiveTrue().stream().map(BreachView::of).toList();
    }
}

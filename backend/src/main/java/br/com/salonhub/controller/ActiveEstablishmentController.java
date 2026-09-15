package br.com.salonhub.controller;

import br.com.salonhub.dto.establishment.ActiveEstablishmentResponse;
import br.com.salonhub.service.ActiveEstablishmentService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/estabelecimentos")
public class ActiveEstablishmentController {
    private final ActiveEstablishmentService activeEstablishmentService;

    public ActiveEstablishmentController(ActiveEstablishmentService activeEstablishmentService) {
        this.activeEstablishmentService = activeEstablishmentService;
    }

    @GetMapping
    public List<ActiveEstablishmentResponse> findAllActive() {
        return activeEstablishmentService.findAll();
    }
}

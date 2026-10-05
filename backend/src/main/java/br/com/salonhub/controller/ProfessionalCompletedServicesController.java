package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.professionalearnings.ProfessionalCompletedServicesResponse;
import br.com.salonhub.service.professionalagenda.ProfessionalCompletedServicesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@PreAuthorize(AccessRules.PROFESSIONAL)
@RestController
@RequestMapping("/api/profissionais/me/atendimentos-realizados")
@Tag(name = "Profissional", description = "Consultas do próprio profissional autenticado")
@RequiredArgsConstructor
public class ProfessionalCompletedServicesController {

    private final ProfessionalCompletedServicesService completedServicesService;

    @GetMapping
    @Operation(
            summary = "Meus atendimentos realizados",
            description = "Atendimentos concluídos do profissional autenticado, com resumo de valores e comissões. "
                    + "Sem período, considera o mês corrente.")
    @ApiResponse(responseCode = "200", description = "Consulta realizada")
    @ApiResponse(responseCode = "400", description = "Período incompleto/inválido ou serviço inexistente")
    @ApiResponse(responseCode = "403", description = "Usuário não é profissional")
    public ProfessionalCompletedServicesResponse findMine(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Long serviceId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return completedServicesService.findMine(Long.valueOf(jwt.getSubject()), startDate, endDate, serviceId,
                Math.max(0, page), Math.max(1, Math.min(size, 100)));
    }
}

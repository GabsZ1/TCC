package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeCreationResponse;
import br.com.salonhub.dto.employee.EmployeeRemovalResponse;
import br.com.salonhub.dto.employee.EmployeeResponse;
import br.com.salonhub.dto.employee.EmployeeSelfProfileUpdateRequest;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesResponse;
import br.com.salonhub.service.employee.EmployeePhotoService;
import br.com.salonhub.service.employee.EmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Expõe as operações HTTP de funcionários e encaminha as regras de negócio aos serviços. */
@PreAuthorize(AccessRules.ADMIN)
@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeePhotoService employeePhotoService;

    /** Cadastra um funcionário na unidade administrada e inicia seu convite. */
    @PostMapping
    public ResponseEntity<EmployeeCreationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.create(currentUserId(jwt), request));
    }

    /** Reenvia instruções de primeiro acesso a um funcionário elegível. */
    @PostMapping("/{id}/reenviar-convite")
    public EmployeeCreationResponse resendInvitation(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return employeeService.resendInvitation(currentUserId(jwt), id);
    }

    /** Lista funcionários da unidade administrada. */
    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.findAll(currentUserId(jwt)));
    }

    /** Consulta serviços atribuídos a um profissional da unidade. */
    @GetMapping("/{id}/servicos")
    public ProfessionalServicesResponse findServices(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return employeeService.findServices(currentUserId(jwt), id);
    }

    /** Substitui os serviços que o profissional pode oferecer. */
    @PutMapping("/{id}/servicos")
    public ProfessionalServicesResponse updateServices(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfessionalServicesRequest request) {
        return employeeService.updateServices(currentUserId(jwt), id, request);
    }

    /** Consulta os serviços do próprio profissional autenticado. */
    @PreAuthorize(AccessRules.PROFESSIONAL)
    @GetMapping("/me/servicos")
    public ProfessionalServicesResponse findMyServices(@AuthenticationPrincipal Jwt jwt) {
        return employeeService.findMyServices(currentUserId(jwt));
    }

    /** Altera o cadastro de um funcionário da unidade. */
    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.update(currentUserId(jwt), id, request));
    }

    /** Solicita a exclusão de um funcionário quando o histórico permite. */
    @DeleteMapping("/{id}")
    public ResponseEntity<EmployeeRemovalResponse> remove(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.remove(currentUserId(jwt), id));
    }

    /** Desativa um funcionário sem apagar seus dados históricos. */
    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<EmployeeResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.deactivate(currentUserId(jwt), id));
    }

    /** Restaura o acesso de um funcionário anteriormente desativado. */
    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<EmployeeResponse> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.reactivate(currentUserId(jwt), id));
    }

    /** Perfil do próprio funcionário (PROFISSIONAL). */
    @PreAuthorize(AccessRules.PROFESSIONAL)
    @GetMapping("/me/perfil")
    public EmployeeResponse findMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return employeeService.findMyProfile(currentUserId(jwt));
    }

    /** Altera apenas os campos editáveis pelo próprio profissional. */
    @PreAuthorize(AccessRules.PROFESSIONAL)
    @PutMapping("/me/perfil")
    public EmployeeResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeSelfProfileUpdateRequest request) {
        return employeeService.updateMyProfile(currentUserId(jwt), request);
    }

    /** Foto: só o próprio funcionário envia (multipart, campo "file") ou remove. */
    @PreAuthorize(AccessRules.PROFESSIONAL)
    @PutMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadMyPhoto(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        employeePhotoService.uploadOwn(currentUserId(jwt), file);
    }

    /** Remove a foto do próprio profissional. */
    @PreAuthorize(AccessRules.PROFESSIONAL)
    @DeleteMapping("/me/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyPhoto(@AuthenticationPrincipal Jwt jwt) {
        employeePhotoService.deleteOwn(currentUserId(jwt));
    }

    /** Administrador do estabelecimento ou o próprio profissional. */
    @PreAuthorize(AccessRules.ESTABLISHMENT_STAFF)
    @GetMapping("/{id}/foto")
    public ResponseEntity<byte[]> findPhoto(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        EmployeePhotoService.StoredPhoto photo = employeePhotoService.find(currentUserId(jwt), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.noStore().cachePrivate())
                .body(photo.data());
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

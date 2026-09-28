package br.com.salonhub.controller;

import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeCreationResponse;
import br.com.salonhub.dto.employee.EmployeeRemovalResponse;
import br.com.salonhub.dto.employee.EmployeeResponse;
import br.com.salonhub.dto.employee.EmployeeSelfProfileUpdateRequest;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesRequest;
import br.com.salonhub.dto.employee.ProfessionalServicesResponse;
import br.com.salonhub.service.EmployeePhotoService;
import br.com.salonhub.service.EmployeeService;
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

@RestController
@RequestMapping("/api/funcionarios")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeePhotoService employeePhotoService;

    @PostMapping
    public ResponseEntity<EmployeeCreationResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.create(currentUserId(jwt), request));
    }

    @GetMapping
    public ResponseEntity<List<EmployeeResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.findAll(currentUserId(jwt)));
    }

    @GetMapping("/{id}/servicos")
    public ProfessionalServicesResponse findServices(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return employeeService.findServices(currentUserId(jwt), id);
    }

    @PutMapping("/{id}/servicos")
    public ProfessionalServicesResponse updateServices(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfessionalServicesRequest request) {
        return employeeService.updateServices(currentUserId(jwt), id, request);
    }

    @GetMapping("/me/servicos")
    public ProfessionalServicesResponse findMyServices(@AuthenticationPrincipal Jwt jwt) {
        return employeeService.findMyServices(currentUserId(jwt));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmployeeResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.update(currentUserId(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<EmployeeRemovalResponse> remove(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.remove(currentUserId(jwt), id));
    }

    @PatchMapping("/{id}/desativacao")
    public ResponseEntity<EmployeeResponse> deactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.deactivate(currentUserId(jwt), id));
    }

    @PatchMapping("/{id}/ativacao")
    public ResponseEntity<EmployeeResponse> reactivate(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.reactivate(currentUserId(jwt), id));
    }

    /** Perfil do próprio funcionário (PROFISSIONAL). */
    @GetMapping("/me/perfil")
    public EmployeeResponse findMyProfile(@AuthenticationPrincipal Jwt jwt) {
        return employeeService.findMyProfile(currentUserId(jwt));
    }

    @PutMapping("/me/perfil")
    public EmployeeResponse updateMyProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeSelfProfileUpdateRequest request) {
        return employeeService.updateMyProfile(currentUserId(jwt), request);
    }

    /** Foto: só o próprio funcionário envia (multipart, campo "file") ou remove. */
    @PutMapping(value = "/me/foto", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void uploadMyPhoto(@AuthenticationPrincipal Jwt jwt, @RequestPart("file") MultipartFile file) {
        employeePhotoService.uploadOwn(currentUserId(jwt), file);
    }

    @DeleteMapping("/me/foto")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteMyPhoto(@AuthenticationPrincipal Jwt jwt) {
        employeePhotoService.deleteOwn(currentUserId(jwt));
    }

    /** Administrador do estabelecimento ou o próprio profissional. */
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

package br.com.salonhub.controller;

import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.employee.EmployeeCreateRequest;
import br.com.salonhub.dto.employee.EmployeeUpdateRequest;
import br.com.salonhub.service.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/funcionarios")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @PostMapping
    public ResponseEntity<AuthenticatedUserResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(employeeService.create(currentUserId(jwt), request));
    }

    @GetMapping
    public ResponseEntity<List<AuthenticatedUserResponse>> findAll(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(employeeService.findAll(currentUserId(jwt)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<AuthenticatedUserResponse> update(
            @PathVariable Long id,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody EmployeeUpdateRequest request) {
        return ResponseEntity.ok(employeeService.update(currentUserId(jwt), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id, @AuthenticationPrincipal Jwt jwt) {
        employeeService.deactivate(currentUserId(jwt), id);
        return ResponseEntity.noContent().build();
    }

    private Long currentUserId(Jwt jwt) {
        return Long.valueOf(jwt.getSubject());
    }
}

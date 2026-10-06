package br.com.salonhub.controller;

import br.com.salonhub.security.AccessRules;
import org.springframework.security.access.prepost.PreAuthorize;

import br.com.salonhub.dto.client.ClientAccountClosureRequest;
import br.com.salonhub.dto.client.ClientProfileResponse;
import br.com.salonhub.dto.client.ClientProfileUpdateRequest;
import br.com.salonhub.service.ClientAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@PreAuthorize(AccessRules.CLIENT)
@RestController
@RequestMapping("/api/clientes/me")
@Tag(name = "Conta do cliente", description = "Perfil e exclusão da própria conta")
@RequiredArgsConstructor
public class ClientAccountController {

    private final ClientAccountService clientAccountService;

    @GetMapping
    @Operation(summary = "Ver meu perfil (US 2.25)")
    @ApiResponse(responseCode = "200", description = "Perfil do cliente autenticado")
    @ApiResponse(responseCode = "403", description = "Usuário não é cliente")
    public ClientProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return clientAccountService.getProfile(Long.valueOf(jwt.getSubject()));
    }

    @PutMapping
    @Operation(summary = "Editar meu perfil (US 2.24)")
    @ApiResponse(responseCode = "200", description = "Perfil atualizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    public ClientProfileResponse updateProfile(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ClientProfileUpdateRequest request) {
        return clientAccountService.updateProfile(Long.valueOf(jwt.getSubject()), request);
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Excluir minha conta (US 2.26)", description = "Exige a senha atual como confirmação.")
    @ApiResponse(responseCode = "204", description = "Conta excluída")
    @ApiResponse(responseCode = "400", description = "Senha incorreta")
    public void closeAccount(
            @AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ClientAccountClosureRequest request) {
        clientAccountService.closeAccount(Long.valueOf(jwt.getSubject()), request);
    }
}

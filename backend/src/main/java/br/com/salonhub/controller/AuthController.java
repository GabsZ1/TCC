package br.com.salonhub.controller;

import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationResponse;
import br.com.salonhub.dto.auth.LoginRequest;
import br.com.salonhub.dto.auth.LoginResponse;
import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.ClientRegistrationRequest;
import br.com.salonhub.dto.auth.FirstAccessRequest;
import br.com.salonhub.service.auth.AuthenticationService;
import br.com.salonhub.service.auth.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Cadastro, login e sessão do usuário")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar estabelecimento e administrador",
            description = "Cria o estabelecimento e seu primeiro usuário ADMIN na mesma transação."
    )
    @ApiResponse(responseCode = "201", description = "Cadastro realizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "CNPJ ou e-mail já cadastrado")
    @SecurityRequirements
    public InitialRegistrationResponse register(@Valid @RequestBody InitialRegistrationRequest request) {
        return registrationService.registerEstablishment(request);
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar cliente", description = "Cria uma conta de cliente independente de estabelecimento.")
    @ApiResponse(responseCode = "201", description = "Cliente cadastrado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    @SecurityRequirements
    public AuthenticatedUserResponse registerClient(@Valid @RequestBody ClientRegistrationRequest request) {
        return registrationService.registerClient(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar", description = "Autentica com e-mail e senha e devolve um JWT.")
    @ApiResponse(responseCode = "200", description = "Login realizado")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    @SecurityRequirements
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authenticationService.authenticate(request);
    }

    @PostMapping("/primeiro-acesso")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Definir senha no primeiro acesso")
    @ApiResponse(responseCode = "204", description = "Senha definida")
    @ApiResponse(responseCode = "400", description = "Token inválido, expirado ou já utilizado")
    @SecurityRequirements
    public void completeFirstAccess(@Valid @RequestBody FirstAccessRequest request) {
        authenticationService.completeFirstAccess(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sessão", description = "Retorna o usuário autenticado e seu estabelecimento.")
    @ApiResponse(responseCode = "200", description = "Usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou usuário inativo")
    public AuthenticatedUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.getCurrentUser(Long.valueOf(jwt.getSubject()));
    }
}

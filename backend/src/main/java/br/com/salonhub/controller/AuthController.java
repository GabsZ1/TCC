package br.com.salonhub.controller;

import br.com.salonhub.dto.auth.InitialRegistrationRequest;
import br.com.salonhub.dto.auth.InitialRegistrationResponse;
import br.com.salonhub.dto.auth.LoginRequest;
import br.com.salonhub.dto.auth.LoginResponse;
import br.com.salonhub.dto.auth.AuthenticatedUserResponse;
import br.com.salonhub.dto.auth.ClientRegistrationRequest;
import br.com.salonhub.dto.auth.FirstAccessRequest;
import br.com.salonhub.dto.auth.PasswordChangeRequest;
import br.com.salonhub.dto.auth.PasswordRecoveryRequest;
import br.com.salonhub.dto.auth.PasswordRecoveryResponse;
import br.com.salonhub.dto.auth.PasswordResetRequest;
import br.com.salonhub.service.auth.AuthenticationRateLimiter;
import br.com.salonhub.service.auth.AuthenticationRateLimiter.PublicForm;
import br.com.salonhub.service.auth.AuthenticationService;
import br.com.salonhub.service.auth.PasswordRecoveryService;
import br.com.salonhub.service.auth.RegistrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação", description = "Cadastro, login e sessão do usuário")
@RequiredArgsConstructor
public class AuthController {

    private final RegistrationService registrationService;
    private final AuthenticationService authenticationService;
    private final PasswordRecoveryService passwordRecoveryService;
    private final AuthenticationRateLimiter rateLimiter;

    @PostMapping("/cadastro")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Cadastrar estabelecimento e administrador",
            description = "Cria o estabelecimento e seu primeiro usuário ADMIN na mesma transação."
    )
    @ApiResponse(responseCode = "201", description = "Cadastro realizado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "CNPJ ou e-mail já cadastrado")
    @ApiResponse(responseCode = "429", description = "Muitos cadastros a partir do mesmo IP")
    @SecurityRequirements
    public InitialRegistrationResponse register(@Valid @RequestBody InitialRegistrationRequest request,
                                                HttpServletRequest httpRequest) {
        rateLimiter.checkPublicForm(PublicForm.ESTABLISHMENT_REGISTRATION, httpRequest.getRemoteAddr());
        return registrationService.registerEstablishment(request);
    }

    @PostMapping("/clientes")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Cadastrar cliente", description = "Cria uma conta de cliente independente de estabelecimento.")
    @ApiResponse(responseCode = "201", description = "Cliente cadastrado")
    @ApiResponse(responseCode = "400", description = "Dados inválidos")
    @ApiResponse(responseCode = "409", description = "E-mail já cadastrado")
    @ApiResponse(responseCode = "429", description = "Muitos cadastros a partir do mesmo IP")
    @SecurityRequirements
    public AuthenticatedUserResponse registerClient(@Valid @RequestBody ClientRegistrationRequest request,
                                                    HttpServletRequest httpRequest) {
        rateLimiter.checkPublicForm(PublicForm.CLIENT_REGISTRATION, httpRequest.getRemoteAddr());
        return registrationService.registerClient(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar", description = "Autentica com e-mail e senha e devolve um JWT.")
    @ApiResponse(responseCode = "200", description = "Login realizado")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    @ApiResponse(responseCode = "429", description = "Login bloqueado temporariamente após tentativas repetidas")
    @SecurityRequirements
    public LoginResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authenticationService.authenticate(request, httpRequest.getRemoteAddr());
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

    @PostMapping("/recuperar-senha")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicitar recuperação de senha", description = "Envia por e-mail um link de redefinição, único e temporário.")
    @ApiResponse(responseCode = "202", description = "Solicitação recebida sem revelar a existência ou estado da conta")
    @ApiResponse(responseCode = "400", description = "Formato de e-mail inválido")
    @ApiResponse(responseCode = "429", description = "Muitos pedidos a partir do mesmo IP")
    @SecurityRequirements
    public PasswordRecoveryResponse requestPasswordReset(@Valid @RequestBody PasswordRecoveryRequest request,
                                                         HttpServletRequest httpRequest) {
        rateLimiter.checkPublicForm(PublicForm.PASSWORD_RECOVERY, httpRequest.getRemoteAddr());
        return passwordRecoveryService.requestReset(request);
    }

    @GetMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Validar link de redefinição", description = "Informa se o link ainda pode ser usado.")
    @ApiResponse(responseCode = "204", description = "Link válido")
    @ApiResponse(responseCode = "400", description = "Link expirado, já usado ou inexistente")
    @SecurityRequirements
    public void validatePasswordResetToken(@RequestParam(required = false) String token) {
        passwordRecoveryService.validateToken(token);
    }

    @PostMapping("/redefinir-senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Redefinir senha", description = "Define uma nova senha usando o link recebido por e-mail.")
    @ApiResponse(responseCode = "204", description = "Senha redefinida")
    @ApiResponse(responseCode = "400", description = "Link inválido/expirado, confirmação divergente ou senha fraca")
    @SecurityRequirements
    public void resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        passwordRecoveryService.resetPassword(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sessão", description = "Retorna o usuário autenticado e seu estabelecimento.")
    @ApiResponse(responseCode = "200", description = "Usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou usuário inativo")
    public AuthenticatedUserResponse getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        return authenticationService.getCurrentUser(Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/senha")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Alterar senha", description = "O usuário autenticado troca a própria senha informando a senha atual.")
    @ApiResponse(responseCode = "204", description = "Senha alterada")
    @ApiResponse(responseCode = "400", description = "Senha atual incorreta, confirmação divergente ou senha fraca")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou usuário inativo")
    public void changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody PasswordChangeRequest request) {
        authenticationService.changePassword(Long.valueOf(jwt.getSubject()), request);
    }
}

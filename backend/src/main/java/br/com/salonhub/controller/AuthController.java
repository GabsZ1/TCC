package br.com.salonhub.controller;

import br.com.salonhub.dto.CadastroInicialRequest;
import br.com.salonhub.dto.CadastroInicialResponse;
import br.com.salonhub.dto.LoginRequest;
import br.com.salonhub.dto.LoginResponse;
import br.com.salonhub.dto.UsuarioAutenticadoResponse;
import br.com.salonhub.service.AuthService;
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

    private final AuthService authService;

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
    public CadastroInicialResponse cadastrar(@Valid @RequestBody CadastroInicialRequest request) {
        return authService.cadastrar(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Entrar", description = "Autentica com e-mail e senha e devolve um JWT.")
    @ApiResponse(responseCode = "200", description = "Login realizado")
    @ApiResponse(responseCode = "401", description = "Credenciais inválidas")
    @SecurityRequirements
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Consultar sessão", description = "Retorna o usuário autenticado e seu estabelecimento.")
    @ApiResponse(responseCode = "200", description = "Usuário autenticado")
    @ApiResponse(responseCode = "401", description = "Token ausente, inválido ou usuário inativo")
    public UsuarioAutenticadoResponse me(@AuthenticationPrincipal Jwt jwt) {
        return authService.me(Long.valueOf(jwt.getSubject()));
    }
}

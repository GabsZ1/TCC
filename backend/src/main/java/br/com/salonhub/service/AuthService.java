package br.com.salonhub.service;

import br.com.salonhub.dto.CadastroInicialRequest;
import br.com.salonhub.dto.CadastroInicialResponse;
import br.com.salonhub.dto.LoginRequest;
import br.com.salonhub.dto.LoginResponse;
import br.com.salonhub.dto.UsuarioAutenticadoResponse;
import br.com.salonhub.entity.Estabelecimento;
import br.com.salonhub.entity.PerfilUsuario;
import br.com.salonhub.entity.Usuario;
import br.com.salonhub.exception.ConflitoException;
import br.com.salonhub.exception.NaoAutorizadoException;
import br.com.salonhub.repository.EstabelecimentoRepository;
import br.com.salonhub.repository.UsuarioRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final EstabelecimentoRepository estabelecimentoRepository;
    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public CadastroInicialResponse cadastrar(CadastroInicialRequest request) {
        String cnpj = somenteDigitos(request.cnpj());
        String emailResponsavel = request.emailResponsavel().trim().toLowerCase(Locale.ROOT);

        if (estabelecimentoRepository.existsByCnpj(cnpj)) {
            throw new ConflitoException("CNPJ já cadastrado");
        }
        if (usuarioRepository.existsByEmailIgnoreCase(emailResponsavel)) {
            throw new ConflitoException("E-mail já cadastrado");
        }

        Estabelecimento estabelecimento = estabelecimentoRepository.save(
                Estabelecimento.builder()
                        .nome(request.nomeEstabelecimento().trim())
                        .cnpj(cnpj)
                        .email(normalizarTextoOpcional(request.emailEstabelecimento()))
                        .telefone(normalizarTextoOpcional(request.telefoneEstabelecimento()))
                        .build()
        );

        Usuario administrador = usuarioRepository.save(
                Usuario.builder()
                        .estabelecimento(estabelecimento)
                        .nome(request.nomeResponsavel().trim())
                        .email(emailResponsavel)
                        .senha(passwordEncoder.encode(request.senha()))
                        .perfil(PerfilUsuario.ADMIN)
                        .build()
        );

        return new CadastroInicialResponse(
                estabelecimento.getId(),
                estabelecimento.getNome(),
                administrador.getId(),
                administrador.getNome(),
                administrador.getEmail()
        );
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(email)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoAutorizadoException("E-mail ou senha inválidos"));

        if (!passwordEncoder.matches(request.senha(), usuario.getSenha())) {
            throw new NaoAutorizadoException("E-mail ou senha inválidos");
        }

        TokenService.TokenGerado token = tokenService.gerar(usuario);
        return new LoginResponse(
                token.valor(),
                "Bearer",
                token.expiraEm(),
                UsuarioAutenticadoResponse.de(usuario)
        );
    }

    @Transactional(readOnly = true)
    public UsuarioAutenticadoResponse me(Long usuarioId) {
        Usuario usuario = usuarioRepository.findById(usuarioId)
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new NaoAutorizadoException("Usuário inválido ou inativo"));
        return UsuarioAutenticadoResponse.de(usuario);
    }

    private String somenteDigitos(String valor) {
        return valor.replaceAll("\\D", "");
    }

    private String normalizarTextoOpcional(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}

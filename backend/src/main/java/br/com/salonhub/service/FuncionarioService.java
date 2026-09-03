package br.com.salonhub.service;

import br.com.salonhub.dto.FuncionarioCreateRequest;
import br.com.salonhub.dto.FuncionarioUpdateRequest;
import br.com.salonhub.dto.UsuarioAutenticadoResponse;
import br.com.salonhub.entity.Estabelecimento;
import br.com.salonhub.entity.PerfilUsuario;
import br.com.salonhub.entity.Usuario;
import br.com.salonhub.exception.ConflitoException;
import br.com.salonhub.repository.EstabelecimentoRepository;
import br.com.salonhub.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class FuncionarioService {

    private final UsuarioRepository usuarioRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;
    private final PasswordEncoder passwordEncoder;

    public FuncionarioService(
            UsuarioRepository usuarioRepository,
            EstabelecimentoRepository estabelecimentoRepository,
            PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UsuarioAutenticadoResponse cadastrar(Long estabelecimentoId, FuncionarioCreateRequest request) {
        if (usuarioRepository.existsByEmailIgnoreCase(request.email())) {
            throw new ConflitoException("E-mail já cadastrado no sistema.");
        }

        Estabelecimento estab = estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> new IllegalArgumentException("Estabelecimento não encontrado."));

        Usuario funcionario = Usuario.builder()
                .nome(request.nome())
                .email(request.email().toLowerCase())
                .senha(passwordEncoder.encode(request.senha()))
                .perfil(PerfilUsuario.PROFISSIONAL)
                .estabelecimento(estab)
                .ativo(true)
                .build();

        return toResponse(usuarioRepository.save(funcionario));
    }

    @Transactional(readOnly = true)
    public List<UsuarioAutenticadoResponse> listarPorEstabelecimento(Long estabelecimentoId) {
        List<Usuario> todos = usuarioRepository.findByEstabelecimentoIdAndPerfil(estabelecimentoId, PerfilUsuario.PROFISSIONAL);
        List<UsuarioAutenticadoResponse> ativos = new ArrayList<>();

        for (Usuario u : todos) {
            if (u.isAtivo()) { // Se está ativo, adiciona na lista
                ativos.add(toResponse(u));
            }
        }

        return ativos;
    }

    @Transactional
    public UsuarioAutenticadoResponse atualizar(Long funcionarioId, FuncionarioUpdateRequest request) {
        Usuario funcionario = usuarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new IllegalArgumentException("Funcionário não encontrado."));

        funcionario.setNome(request.nome());
        funcionario.setEmail(request.email().toLowerCase());

        return toResponse(usuarioRepository.save(funcionario));
    }

    @Transactional
    public void desativar(Long funcionarioId) {
        Usuario funcionario = usuarioRepository.findById(funcionarioId)
                .orElseThrow(() -> new IllegalArgumentException("Funcionário não encontrado."));

        funcionario.setAtivo(false);
        usuarioRepository.save(funcionario);
    }

    private UsuarioAutenticadoResponse toResponse(Usuario u) {
        return new UsuarioAutenticadoResponse(
                u.getId(),
                u.getNome(),
                u.getEmail(),
                u.getPerfil(),
                u.getEstabelecimento().getId(),
                u.getEstabelecimento().getNome()
        );
    }
}

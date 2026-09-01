package br.com.salonhub.dto;

import br.com.salonhub.entity.PerfilUsuario;
import br.com.salonhub.entity.Usuario;

public record UsuarioAutenticadoResponse(
        Long id,
        String nome,
        String email,
        PerfilUsuario perfil,
        Long estabelecimentoId,
        String nomeEstabelecimento
) {
    public static UsuarioAutenticadoResponse de(Usuario usuario) {
        return new UsuarioAutenticadoResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getEmail(),
                usuario.getPerfil(),
                usuario.getEstabelecimento().getId(),
                usuario.getEstabelecimento().getNome()
        );
    }
}

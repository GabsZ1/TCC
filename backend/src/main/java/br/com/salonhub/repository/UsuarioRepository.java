package br.com.salonhub.repository;

import br.com.salonhub.entity.PerfilUsuario;
import br.com.salonhub.entity.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UsuarioRepository extends JpaRepository<Usuario, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Usuario> findByEmailIgnoreCase(String email);


    List<Usuario> findByEstabelecimentoIdAndPerfil(Long estabelecimentoId, PerfilUsuario perfil);
}

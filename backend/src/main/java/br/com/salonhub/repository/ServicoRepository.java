package br.com.salonhub.repository;

import br.com.salonhub.entity.Servico;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ServicoRepository extends JpaRepository<Servico, Long> {

    List<Servico> findByEstabelecimentoIdAndAtivoTrue(Long estabelecimentoId);

    Optional<Servico> findByIdAndEstabelecimentoId(Long id, Long estabelecimentoId);
}

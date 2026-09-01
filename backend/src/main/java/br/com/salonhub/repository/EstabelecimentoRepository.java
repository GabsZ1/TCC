package br.com.salonhub.repository;

import br.com.salonhub.entity.Estabelecimento;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EstabelecimentoRepository extends JpaRepository<Estabelecimento, Long> {

    boolean existsByCnpj(String cnpj);
}

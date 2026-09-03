package br.com.salonhub.service;

import br.com.salonhub.dto.ServicoRequest;
import br.com.salonhub.dto.ServicoResponse;
import br.com.salonhub.entity.Estabelecimento;
import br.com.salonhub.entity.Servico;
import br.com.salonhub.repository.EstabelecimentoRepository;
import br.com.salonhub.repository.ServicoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ServicoService {

    private final ServicoRepository servicoRepository;
    private final EstabelecimentoRepository estabelecimentoRepository;

    public ServicoService(ServicoRepository servicoRepository, EstabelecimentoRepository estabelecimentoRepository) {
        this.servicoRepository = servicoRepository;
        this.estabelecimentoRepository = estabelecimentoRepository;
    }

    @Transactional
    public ServicoResponse criar(Long estabelecimentoId, ServicoRequest request) {
        Estabelecimento estab = estabelecimentoRepository.findById(estabelecimentoId)
                .orElseThrow(() -> new RuntimeException("Estabelecimento não encontrado"));

        Servico servico = Servico.builder()
                .estabelecimento(estab)
                .nome(request.nome())
                .descricao(request.descricao())
                .preco(request.preco())
                .comissaoPorcentagem(request.comissaoPorcentagem())
                .ativo(true)
                .build();

        return ServicoResponse.fromEntity(servicoRepository.save(servico));
    }

    @Transactional(readOnly = true)
    public List<ServicoResponse> listarTodos(Long estabelecimentoId) {
        return servicoRepository.findByEstabelecimentoIdAndAtivoTrue(estabelecimentoId)
                .stream()
                .map(ServicoResponse::fromEntity)
                .toList();
    }

    @Transactional
    public ServicoResponse atualizar(Long id, Long estabelecimentoId, ServicoRequest request) {
        Servico servico = servicoRepository.findByIdAndEstabelecimentoId(id, estabelecimentoId)
                .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

        servico.setNome(request.nome());
        servico.setDescricao(request.descricao());
        servico.setPreco(request.preco());
        servico.setComissaoPorcentagem(request.comissaoPorcentagem());

        return ServicoResponse.fromEntity(servicoRepository.save(servico));
    }

    @Transactional
    public void inativar(Long id, Long estabelecimentoId) {
        Servico servico = servicoRepository.findByIdAndEstabelecimentoId(id, estabelecimentoId)
                .orElseThrow(() -> new RuntimeException("Serviço não encontrado"));

        servico.setAtivo(false);
        servicoRepository.save(servico);
    }
}

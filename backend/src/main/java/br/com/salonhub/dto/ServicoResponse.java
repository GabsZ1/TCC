package br.com.salonhub.dto;

import br.com.salonhub.entity.Servico;
import java.math.BigDecimal;

public record ServicoResponse(
        Long id,
        String nome,
        String descricao,
        BigDecimal preco,
        BigDecimal comissaoPorcentagem,
        Boolean ativo
) {
    public static ServicoResponse fromEntity(Servico servico) {
        return new ServicoResponse(
                servico.getId(),
                servico.getNome(),
                servico.getDescricao(),
                servico.getPreco(),
                servico.getComissaoPorcentagem(),
                servico.getAtivo()
        );
    }
}

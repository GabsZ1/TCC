package br.com.salonhub.dto;

public record CadastroInicialResponse(
        Long estabelecimentoId,
        String nomeEstabelecimento,
        Long usuarioId,
        String nomeResponsavel,
        String emailResponsavel
) {
}

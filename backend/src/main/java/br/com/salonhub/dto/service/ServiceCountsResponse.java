package br.com.salonhub.dto.service;

/** Define os dados devolvidos pela API no fluxo de serviços, sem expor as entidades persistidas. */
public record ServiceCountsResponse(long total, long active, long inactive) {
}

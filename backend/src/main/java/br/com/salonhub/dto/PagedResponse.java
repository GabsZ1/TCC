package br.com.salonhub.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Define o contrato mínimo e estável de paginação consumido pelo frontend. */
public record PagedResponse<T>(List<T> content, int number, int totalPages, long totalElements) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }
}

package br.com.salonhub.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/** Stable, minimal pagination contract for the React client. */
public record PagedResponse<T>(List<T> content, int number, int totalPages, long totalElements) {
    public static <T> PagedResponse<T> from(Page<T> page) {
        return new PagedResponse<>(page.getContent(), page.getNumber(), page.getTotalPages(), page.getTotalElements());
    }
}

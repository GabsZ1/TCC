package br.com.salonhub.dto.employee;

/** Resultado de uma exclusão definitiva bem-sucedida. Vínculos impedem a exclusão. */
public record EmployeeRemovalResponse(
        boolean removedPermanently,
        String message
) {
}

package br.com.salonhub.dto.employee;

/**
 * Resultado da remoção de um funcionário: se ele não possui atendimentos
 * vinculados, é removido permanentemente; caso contrário, é apenas
 * desativado para preservar o histórico (RN 2.3.2 / CT04 do TCC).
 */
public record EmployeeRemovalResponse(
        boolean removedPermanently,
        String message
) {
}

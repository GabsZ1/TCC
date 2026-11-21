package br.com.salonhub.service.report;

import br.com.salonhub.entity.User;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Obtém os dados do cabeçalho enquanto o vínculo com a unidade está carregado. */
@Service
@RequiredArgsConstructor
public class ReportHeaderService {
    private final TenantAuthorizationService authorization;

    @Transactional(readOnly = true)
    public ReportHeader forAdministrator(Long administratorId) {
        User administrator = authorization.requireAdministrator(administratorId);
        return new ReportHeader(administrator.getEstablishment().getName(), administrator.getName());
    }
}

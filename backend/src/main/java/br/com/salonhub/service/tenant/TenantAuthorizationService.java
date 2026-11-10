package br.com.salonhub.service.tenant;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.UnauthorizedException;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Confere papel e vínculo com o estabelecimento antes de acessar dados protegidos. */
@Service
@RequiredArgsConstructor
public class TenantAuthorizationService {

    private final UserRepository userRepository;

    /** Exige o papel global, sem aceitar vínculos administrativos apenas locais. */
    @Transactional(readOnly = true)
    public User requireSystemAdministrator(Long userId) {
        User user = requireActiveUser(userId);

        if (user.getRole() != UserRole.SYSTEM_ADMIN) {
            throw new AccessDeniedException("Acesso permitido apenas para administradores do sistema");
        }
        return user;
    }

    /** Exige administrador local ativo e seu vínculo com uma unidade. */
    @Transactional(readOnly = true)
    public User requireAdministrator(Long userId) {
        User user = requireActiveUser(userId);

        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Acesso permitido apenas para administradores");
        }
        if (user.getEstablishment() == null) {
            throw new AccessDeniedException("O administrador não possui estabelecimento vinculado.");
        }
        if (!user.getEstablishment().isActive()) {
            throw new AccessDeniedException("O estabelecimento foi desativado pelo administrador do sistema.");
        }

        return user;
    }

    /** Exige conta ativa com papel de cliente. */
    @Transactional(readOnly = true)
    public User requireClient(Long userId) {
        User user = requireActiveUser(userId);

        if (user.getRole() != UserRole.CLIENT) {
            throw new AccessDeniedException("Acesso permitido apenas para clientes");
        }

        return user;
    }

    /** Exige profissional ativo vinculado a um estabelecimento. */
    @Transactional(readOnly = true)
    public User requireProfessional(Long userId) {
        User user = requireActiveUser(userId);

        if (user.getRole() != UserRole.PROFESSIONAL) {
            throw new AccessDeniedException("Acesso permitido apenas para profissionais");
        }
        if (user.getEstablishment() == null || !user.getEstablishment().isActive()) {
            throw new AccessDeniedException("O estabelecimento foi desativado pelo administrador do sistema.");
        }
        if (user.getEstablishment().isPaused()) {
            throw new AccessDeniedException("O estabelecimento está pausado. O acesso dos funcionários está bloqueado.");
        }

        return user;
    }

    /** Aceita apenas equipe ativa e vinculada à unidade antes de operar seus dados. */
    @Transactional(readOnly = true)
    public User requireEstablishmentStaff(Long userId) {
        User user = requireActiveUser(userId);
        if (user.getRole() == UserRole.ADMIN) return requireAdministrator(userId);
        if (user.getRole() == UserRole.PROFESSIONAL) return requireProfessional(userId);
        throw new AccessDeniedException("Acesso permitido apenas à equipe do estabelecimento.");
    }

    private User requireActiveUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou desativado"));
    }
}

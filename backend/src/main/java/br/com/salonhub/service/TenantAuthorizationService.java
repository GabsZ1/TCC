package br.com.salonhub.service;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.UnauthorizedException;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantAuthorizationService {

    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public User requireSystemAdministrator(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

        if (user.getRole() != UserRole.SYSTEM_ADMIN) {
            throw new AccessDeniedException("Acesso permitido apenas para administradores do sistema");
        }
        return user;
    }

    @Transactional(readOnly = true)
    public User requireAdministrator(Long userId) {
        return requireAdministratorIdentity(userId);
    }

    @Transactional(readOnly = true)
    public User requireAdministratorIdentity(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

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

    @Transactional(readOnly = true)
    public User requireClient(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

        if (user.getRole() != UserRole.CLIENT) {
            throw new AccessDeniedException("Acesso permitido apenas para clientes");
        }

        return user;
    }

    @Transactional(readOnly = true)
    public User requireProfessionalAtActiveEstablishment(Long userId) {
        return requireProfessional(userId);
    }

    @Transactional(readOnly = true)
    public User requireProfessional(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

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
}

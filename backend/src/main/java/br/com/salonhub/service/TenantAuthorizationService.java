package br.com.salonhub.service;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.UnauthorizedException;
import br.com.salonhub.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TenantAuthorizationService {

    private final UserRepository userRepository;

    public TenantAuthorizationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public User requireAdministrator(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

        if (user.getRole() != UserRole.ADMIN) {
            throw new AccessDeniedException("Acesso permitido apenas para administradores");
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
    public User requireProfessional(Long userId) {
        User user = userRepository.findById(userId)
                .filter(User::isActive)
                .orElseThrow(() -> new UnauthorizedException("Usuário inválido ou inativo"));

        if (user.getRole() != UserRole.PROFESSIONAL) {
            throw new AccessDeniedException("Acesso permitido apenas para profissionais");
        }

        return user;
    }
}

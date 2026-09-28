package br.com.salonhub.service;

import br.com.salonhub.entity.Media;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Foto do funcionário (opcional). Só o próprio funcionário envia ou remove a foto;
 * o administrador do estabelecimento apenas visualiza. O arquivo fica no armazenamento S3 e o
 * vínculo em users.photo_id → media.
 */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    private final TenantAuthorizationService tenantAuthorizationService;
    private final UserRepository userRepository;
    private final MediaService mediaService;

    @Transactional
    public void uploadOwn(Long professionalId, MultipartFile file) {
        User employee = userRepository.findByIdForUpdate(
                tenantAuthorizationService.requireProfessional(professionalId).getId()).orElseThrow();
        Media previous = employee.getPhoto();
        Media uploaded = mediaService.storeImage(file, employee.getEstablishment(), employee);
        employee.replacePhoto(uploaded);
        if (previous != null) {
            userRepository.flush();
            mediaService.delete(previous);
        }
    }

    @Transactional
    public void deleteOwn(Long professionalId) {
        User employee = userRepository.findByIdForUpdate(
                tenantAuthorizationService.requireProfessional(professionalId).getId()).orElseThrow();
        Media previous = employee.getPhoto();
        if (previous != null) {
            employee.replacePhoto(null);
            userRepository.flush();
            mediaService.delete(previous);
        }
    }

    /** O administrador do estabelecimento ou o próprio profissional podem ver a foto. */
    @Transactional(readOnly = true)
    public StoredPhoto find(Long requesterId, Long employeeId) {
        User requester = userRepository.findById(requesterId)
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Acesso negado"));
        User employee;
        if (requester.getId().equals(employeeId) && requester.getRole() == UserRole.PROFESSIONAL) {
            employee = requester;
        } else {
            User administrator = tenantAuthorizationService.requireAdministrator(requesterId);
            employee = userRepository.findByIdAndEstablishmentIdAndRole(
                            employeeId, administrator.getEstablishment().getId(), UserRole.PROFESSIONAL)
                    .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
        }
        Media photo = employee.getPhoto();
        if (photo == null) {
            throw new ResourceNotFoundException("O funcionário não possui foto.");
        }
        try {
            return new StoredPhoto(photo.getMimeType(), mediaService.load(photo));
        } catch (StorageException exception) {
            if (exception.isNotFound()) {
                throw new ResourceNotFoundException("O funcionário não possui foto.");
            }
            throw exception;
        }
    }

    public record StoredPhoto(String contentType, byte[] data) {
    }
}

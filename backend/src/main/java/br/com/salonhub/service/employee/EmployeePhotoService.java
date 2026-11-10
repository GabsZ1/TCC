package br.com.salonhub.service.employee;

import br.com.salonhub.service.media.MediaService;
import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.entity.Establishment;
import br.com.salonhub.entity.Media;
import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.EstablishmentRepository;
import br.com.salonhub.repository.UserRepository;
import br.com.salonhub.storage.StorageException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * Foto do funcionário (opcional). Só o próprio funcionário envia ou remove a foto;
 * o administrador do estabelecimento apenas visualiza, e os clientes a veem no catálogo
 * (escolha do profissional no agendamento). O arquivo fica no armazenamento S3 e o
 * vínculo em users.photo_id → media.
 */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    private final TenantAuthorizationService tenantAuthorizationService;
    private final UserRepository userRepository;
    private final EstablishmentRepository establishmentRepository;
    private final MediaService mediaService;

    /** Permite ao profissional trocar a própria foto e remove a mídia anterior. */
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

    /** Remove a foto do profissional sem alterar os demais dados de perfil. */
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
        return load(employee);
    }

    /**
     * Foto exibida no catálogo: qualquer usuário autenticado vê, mas só de profissionais
     * ativos de um estabelecimento que aceita agendamentos (as mesmas regras da listagem).
     */
    @Transactional(readOnly = true)
    public StoredPhoto findForCatalog(Long establishmentId, Long professionalId) {
        establishmentRepository.findById(establishmentId)
                .filter(Establishment::acceptsNewAppointments)
                .orElseThrow(() -> new ResourceNotFoundException("Estabelecimento não encontrado."));
        User professional = userRepository.findByIdAndEstablishmentIdAndRole(
                        professionalId, establishmentId, UserRole.PROFESSIONAL)
                .filter(User::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrado."));
        return load(professional);
    }

    private StoredPhoto load(User employee) {
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

package br.com.salonhub.service;

import br.com.salonhub.entity.User;
import br.com.salonhub.entity.UserPhoto;
import br.com.salonhub.entity.UserRole;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.UserPhotoRepository;
import br.com.salonhub.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Arrays;

/**
 * Foto do funcionário (US 2.1/2.2, campo opcional). Aceita JPG, PNG ou WEBP de até 2 MB,
 * conferindo a assinatura do arquivo (não só a extensão/content-type informados).
 */
@Service
@RequiredArgsConstructor
public class EmployeePhotoService {

    static final long MAX_BYTES = 2L * 1024 * 1024;

    private final TenantAuthorizationService tenantAuthorizationService;
    private final UserRepository userRepository;
    private final UserPhotoRepository photoRepository;
    private final Clock clock;

    @Transactional
    public void upload(Long administratorId, Long employeeId, MultipartFile file) {
        User employee = findEmployeeOfAdministrator(administratorId, employeeId);
        if (file == null || file.isEmpty()) {
            throw new InvalidRequestException("Selecione uma imagem.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new InvalidRequestException("A foto deve ter no máximo 2 MB.");
        }
        byte[] data;
        try {
            data = file.getBytes();
        } catch (IOException exception) {
            throw new InvalidRequestException("Não foi possível ler a imagem enviada.");
        }
        String contentType = detectImageType(data);
        if (contentType == null) {
            throw new InvalidRequestException("Formato de imagem não suportado. Use JPG, PNG ou WEBP.");
        }

        OffsetDateTime now = OffsetDateTime.now(clock);
        photoRepository.findById(employee.getId()).ifPresentOrElse(
                photo -> photo.replace(contentType, data, now),
                () -> photoRepository.save(new UserPhoto(employee.getId(), contentType, data, now)));
    }

    @Transactional
    public void delete(Long administratorId, Long employeeId) {
        User employee = findEmployeeOfAdministrator(administratorId, employeeId);
        photoRepository.findById(employee.getId()).ifPresent(photoRepository::delete);
    }

    /** O administrador do estabelecimento ou o próprio profissional podem ver a foto. */
    @Transactional(readOnly = true)
    public UserPhoto find(Long requesterId, Long employeeId) {
        User requester = userRepository.findById(requesterId)
                .filter(User::isActive)
                .orElseThrow(() -> new AccessDeniedException("Acesso negado"));
        boolean isSelf = requester.getId().equals(employeeId) && requester.getRole() == UserRole.PROFESSIONAL;
        if (!isSelf) {
            findEmployeeOfAdministrator(requesterId, employeeId);
        }
        return photoRepository.findById(employeeId)
                .orElseThrow(() -> new ResourceNotFoundException("O funcionário não possui foto."));
    }

    private User findEmployeeOfAdministrator(Long administratorId, Long employeeId) {
        User administrator = tenantAuthorizationService.requireAdministrator(administratorId);
        return userRepository.findByIdAndEstablishmentIdAndRole(
                        employeeId, administrator.getEstablishment().getId(), UserRole.PROFESSIONAL)
                .orElseThrow(() -> new ResourceNotFoundException("Funcionário não encontrado."));
    }

    static String detectImageType(byte[] data) {
        if (startsWith(data, 0xFF, 0xD8, 0xFF)) {
            return "image/jpeg";
        }
        if (startsWith(data, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "image/png";
        }
        if (data.length >= 12 && startsWith(data, 'R', 'I', 'F', 'F')
                && Arrays.equals(Arrays.copyOfRange(data, 8, 12), new byte[]{'W', 'E', 'B', 'P'})) {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int... signature) {
        if (data.length < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if ((data[index] & 0xFF) != signature[index]) {
                return false;
            }
        }
        return true;
    }
}

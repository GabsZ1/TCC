package br.com.salonhub.service.establishment;

import br.com.salonhub.service.tenant.TenantAuthorizationService;
import br.com.salonhub.entity.AppointmentStatus;
import br.com.salonhub.entity.EstablishmentCalendarBlock;
import br.com.salonhub.exception.ConflictException;
import br.com.salonhub.exception.InvalidRequestException;
import br.com.salonhub.exception.ResourceNotFoundException;
import br.com.salonhub.repository.AppointmentRepository;
import br.com.salonhub.repository.EstablishmentCalendarBlockRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/** Cria e consulta períodos bloqueados na agenda do estabelecimento. */
@Service
@RequiredArgsConstructor
public class EstablishmentCalendarBlockService {
    private final TenantAuthorizationService authorization;
    private final EstablishmentCalendarBlockRepository blocks;
    private final AppointmentRepository appointments;

    public record Request(LocalDateTime startsAt, LocalDateTime endsAt, String reason) {}
    public record Response(Long id, LocalDateTime startsAt, LocalDateTime endsAt, String reason) {
        public static Response from(EstablishmentCalendarBlock block) {
            return new Response(block.getId(), block.getStartsAt(), block.getEndsAt(), block.getReason());
        }
    }

    /** Lista bloqueios que se sobrepõem ao período solicitado. */
    @Transactional(readOnly = true)
    public List<Response> list(Long administratorId, LocalDateTime start, LocalDateTime end) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        if (start == null || end == null || !start.isBefore(end) || start.plusDays(366).isBefore(end))
            throw new InvalidRequestException("Informe um período válido de até 366 dias.");
        return blocks.findByEstablishmentIdAndStartsAtLessThanAndEndsAtGreaterThanOrderByStartsAt(establishmentId, end, start)
                .stream().map(Response::from).toList();
    }

    /** Impede criar um bloqueio que se sobreponha a agendamentos confirmados. */
    @Transactional
    public Response create(Long administratorId, Request request) {
        var administrator = authorization.requireAdministrator(administratorId);
        validate(request, administrator.getEstablishment().getId());
        return Response.from(blocks.save(EstablishmentCalendarBlock.create(administrator.getEstablishment(),
                request.startsAt(), request.endsAt(), request.reason())));
    }

    /** Revalida período e conflito antes de alterar um bloqueio existente. */
    @Transactional
    public Response update(Long administratorId, Long id, Request request) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        EstablishmentCalendarBlock block = blocks.findByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueio não encontrado."));
        validate(request, establishmentId);
        block.update(request.startsAt(), request.endsAt(), request.reason());
        return Response.from(block);
    }

    /** Remove apenas um bloqueio do estabelecimento administrado pela sessão. */
    @Transactional
    public void delete(Long administratorId, Long id) {
        Long establishmentId = authorization.requireAdministrator(administratorId).getEstablishment().getId();
        EstablishmentCalendarBlock block = blocks.findByIdAndEstablishmentId(id, establishmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Bloqueio não encontrado."));
        blocks.delete(block);
    }

    /** Compartilha as regras de período, motivo e conflito entre criação e edição. */
    private void validate(Request request, Long establishmentId) {
        if (request == null || request.startsAt() == null || request.endsAt() == null
                || !request.startsAt().isBefore(request.endsAt()) || request.reason() == null
                || request.reason().isBlank() || request.reason().trim().length() > 200)
            throw new InvalidRequestException("Informe início, fim e motivo válidos para o bloqueio.");
        if (appointments.existsByEstablishmentIdAndStatusAndStartsAtLessThanAndEndsAtGreaterThan(establishmentId,
                AppointmentStatus.CONFIRMED, request.endsAt(), request.startsAt()))
            throw new ConflictException("Existem agendamentos confirmados nesse período. Resolva-os antes de bloquear o estabelecimento.");
    }
}

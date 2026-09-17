package br.com.salonhub.exception;

import br.com.salonhub.dto.common.ApiError;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError(409, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiError> handleDataIntegrity(DataIntegrityViolationException exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof ConstraintViolationException violation) {
                String constraint = violation.getConstraintName();
                String message = constraint == null ? null : switch (constraint) {
                    case "uk_users_email" -> "E-mail já cadastrado no sistema.";
                    case "uk_establishments_cnpj" -> "CNPJ já cadastrado.";
                    case "uk_salon_services_establishment_name" -> "Já existe um serviço com esse nome neste estabelecimento.";
                    case "uk_business_hours_establishment_day" -> "Já existe um horário cadastrado para este dia.";
                    default -> null;
                };
                if (message != null) {
                    return handleConflict(new ConflictException(message));
                }
            }
            if (cause instanceof SQLException sqlException) {
                if ("23505".equals(sqlException.getSQLState())) {
                    return handleConflict(new ConflictException("Já existe um registro com os dados informados."));
                }
            }
        }
        throw exception;
    }

    @ExceptionHandler(InvalidRequestException.class)
    public ResponseEntity<ApiError> handleInvalidRequest(InvalidRequestException exception) {
        return ResponseEntity.badRequest().body(
                new ApiError(400, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiError> handleUnauthorized(UnauthorizedException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError(401, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(
                new ApiError(403, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(
                new ApiError(404, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> fields = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(
                error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(
                new ApiError(400, "Dados inválidos", fields, OffsetDateTime.now())
        );
    }

}

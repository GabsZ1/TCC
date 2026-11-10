package br.com.salonhub.exception;

import br.com.salonhub.dto.common.ApiError;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/** Converte falhas de validação e negócio em respostas HTTP consistentes. */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String UNEXPECTED_ERROR = "Ocorreu um erro inesperado. Tente novamente em instantes.";

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
                    case "uk_users_cpf" -> "Já existe um funcionário cadastrado com este CPF.";
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
        log.error("Violação de integridade não mapeada", exception);
        return friendly(HttpStatus.CONFLICT, "Não foi possível concluir a operação. Verifique os dados informados.");
    }

    @ExceptionHandler(TooManyRequestsException.class)
    public ResponseEntity<ApiError> handleTooManyRequests(TooManyRequestsException exception) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS);
        if (exception.getRetryAfter() != null) {
            response.header(HttpHeaders.RETRY_AFTER,
                    String.valueOf(Math.max(1, exception.getRetryAfter().toSeconds())));
        }
        return response.body(
                new ApiError(429, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
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

    /** Recusa do @PreAuthorize (perfil sem acesso ao endpoint): a mensagem padrão do Spring é em inglês. */
    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ApiError> handleAuthorizationDenied(AuthorizationDeniedException exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(
                403, "Seu perfil não tem permissão para acessar este recurso.", Map.of(), OffsetDateTime.now()));
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

    // ---- Erros técnicos: nunca repassar a mensagem interna do Spring ao usuário ----

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException exception) {
        log.warn("Rota não encontrada: {} {}", exception.getHttpMethod(), exception.getResourcePath());
        return friendly(HttpStatus.NOT_FOUND, "O recurso solicitado não está disponível.");
    }

    @ExceptionHandler(br.com.salonhub.storage.StorageException.class)
    public ResponseEntity<ApiError> handleStorage(br.com.salonhub.storage.StorageException exception) {
        log.error("Falha no armazenamento de imagens", exception);
        return friendly(HttpStatus.SERVICE_UNAVAILABLE,
                "O armazenamento de imagens está indisponível no momento. Tente novamente em instantes.");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception) {
        return friendly(HttpStatus.BAD_REQUEST, "A foto deve ter no máximo 2 MB.");
    }

    @ExceptionHandler({MissingServletRequestPartException.class, MultipartException.class})
    public ResponseEntity<ApiError> handleMissingUpload(Exception exception) {
        return friendly(HttpStatus.BAD_REQUEST, "Selecione uma imagem.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException exception) {
        return friendly(HttpStatus.METHOD_NOT_ALLOWED, "Operação não permitida para este recurso.");
    }

    @ExceptionHandler({
            TypeMismatchException.class,
            MissingServletRequestParameterException.class,
            HttpMessageNotReadableException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception) {
        return friendly(HttpStatus.BAD_REQUEST, "Requisição inválida. Verifique os dados informados.");
    }

    /**
     * Último recurso: qualquer erro não previsto vira uma mensagem genérica (o detalhe
     * vai só para o log). Exceções do Spring que já carregam um status HTTP mantêm o status.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception exception) {
        if (exception instanceof ErrorResponse errorResponse && errorResponse.getStatusCode().is4xxClientError()) {
            HttpStatus status = HttpStatus.valueOf(errorResponse.getStatusCode().value());
            return friendly(status, "Não foi possível processar a solicitação.");
        }
        log.error("Erro inesperado na API", exception);
        return friendly(HttpStatus.INTERNAL_SERVER_ERROR, UNEXPECTED_ERROR);
    }

    private static ResponseEntity<ApiError> friendly(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(
                new ApiError(status.value(), message, Map.of(), OffsetDateTime.now()));
    }
}

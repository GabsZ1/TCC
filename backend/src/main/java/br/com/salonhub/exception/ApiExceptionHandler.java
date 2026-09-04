package br.com.salonhub.exception;

import br.com.salonhub.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
                error -> fields.putIfAbsent(toExternalFieldName(error.getField()), error.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(
                new ApiError(400, "Dados inválidos", fields, OffsetDateTime.now())
        );
    }

    private String toExternalFieldName(String fieldName) {
        return switch (fieldName) {
            case "ownerName" -> "nomeResponsavel";
            case "ownerEmail" -> "emailResponsavel";
            case "password" -> "senha";
            case "establishmentName" -> "nomeEstabelecimento";
            case "establishmentEmail" -> "emailEstabelecimento";
            case "establishmentPhone" -> "telefoneEstabelecimento";
            case "name" -> "nome";
            case "description" -> "descricao";
            case "price" -> "preco";
            case "commissionPercentage" -> "comissaoPorcentagem";
            default -> fieldName;
        };
    }
}

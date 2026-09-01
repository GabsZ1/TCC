package br.com.salonhub.exception;

import br.com.salonhub.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ApiError> tratarConflito(ConflitoException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(
                new ApiError(409, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(NaoAutorizadoException.class)
    public ResponseEntity<ApiError> tratarNaoAutorizado(NaoAutorizadoException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                new ApiError(401, exception.getMessage(), Map.of(), OffsetDateTime.now())
        );
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> tratarValidacao(MethodArgumentNotValidException exception) {
        Map<String, String> campos = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(
                erro -> campos.putIfAbsent(erro.getField(), erro.getDefaultMessage())
        );

        return ResponseEntity.badRequest().body(
                new ApiError(400, "Dados inválidos", campos, OffsetDateTime.now())
        );
    }
}

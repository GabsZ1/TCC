package br.com.salonhub.dto;

import java.time.Instant;

public record LoginResponse(
        String token,
        String tipo,
        Instant expiraEm,
        UsuarioAutenticadoResponse usuario
) {
}

package br.com.salonhub.service;

import br.com.salonhub.entity.User;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final Clock clock;

    public TokenService(
            JwtEncoder jwtEncoder,
            @Qualifier("firstAccessJwtDecoder") JwtDecoder jwtDecoder,
            Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.jwtDecoder = jwtDecoder;
        this.clock = clock;
    }

    @Value("${app.jwt.expiration-minutes}")
    private long expirationMinutes;

    @Value("${app.first-access.expiration-hours:24}")
    private long firstAccessExpirationHours;

    public GeneratedToken generate(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(expirationMinutes, ChronoUnit.MINUTES);

        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("salonhub")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("userId", user.getId())
                .claim("role", user.getRole().name())
                .claim("email", user.getEmail());

        if (user.getEstablishment() != null) {
            claimsBuilder.claim("establishmentId", user.getEstablishment().getId());
        }

        JwtClaimsSet claims = claimsBuilder.build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new GeneratedToken(value, expiresAt);
    }

    public GeneratedToken generateFirstAccess(User user) {
        Instant issuedAt = Instant.now(clock);
        Instant expiresAt = issuedAt.plus(firstAccessExpirationHours, ChronoUnit.HOURS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("salonhub")
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .subject(user.getId().toString())
                .claim("purpose", "FIRST_ACCESS")
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String value = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new GeneratedToken(value, expiresAt);
    }

    public Long validateFirstAccess(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            if (!"FIRST_ACCESS".equals(jwt.getClaimAsString("purpose"))) {
                throw new IllegalArgumentException("Token inválido");
            }
            return Long.valueOf(jwt.getSubject());
        } catch (JwtException | IllegalArgumentException exception) {
            return null;
        }
    }

    public record GeneratedToken(String value, Instant expiresAt) {
    }
}

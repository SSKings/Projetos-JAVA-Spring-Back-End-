package com.sskings.ecommerce.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.sskings.ecommerce.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret-with-at-least-32-characters!!";
    private static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    private static SecurityProperties props(String secret, long minutes) {
        return new SecurityProperties(new SecurityProperties.Jwt(secret, minutes), "k", false,
                new SecurityProperties.Login(5, 15), new SecurityProperties.RateLimit(true, 20, 120));
    }

    private final JwtService service = new JwtService(props(SECRET, 120), Clock.fixed(NOW, ZoneOffset.UTC));
    private final User user = User.builder().email("ana@a.com").tokenVersion(4).build();

    @Test
    @DisplayName("Gera e valida token com email e versão")
    void roundTrip() {
        JwtService.TokenClaims claims = new JwtService(props(SECRET, 120), Clock.systemUTC()).validateToken(
                new JwtService(props(SECRET, 120), Clock.systemUTC()).generateToken(user));
        assertThat(claims).isEqualTo(new JwtService.TokenClaims("ana@a.com", 4));
    }

    @Test
    @DisplayName("Expiração configurável")
    void expiration() {
        var decoded = JWT.decode(service.generateToken(user));
        assertThat(decoded.getExpiresAtAsInstant()).isEqualTo(NOW.plus(120, ChronoUnit.MINUTES));
        assertThat(decoded.getIssuedAtAsInstant()).isEqualTo(NOW);
        // token expirado (relógio fixo no passado) é recusado
        assertThat(new JwtService(props(SECRET, 120), Clock.systemUTC()).validateToken(service.generateToken(user))).isNull();
    }

    @Test
    @DisplayName("Assinatura com outro segredo, token adulterado, sem versão ou outro issuer → null")
    void invalidTokens() {
        JwtService live = new JwtService(props(SECRET, 120), Clock.systemUTC());
        String other = new JwtService(props("another-secret-with-32-characters-xx", 120), Clock.systemUTC()).generateToken(user);
        assertThat(live.validateToken(other)).isNull();
        assertThat(live.validateToken("not.a.jwt")).isNull();

        Algorithm alg = Algorithm.HMAC256(SECRET);
        String noVersion = JWT.create().withIssuer("ecommerce-api").withSubject("ana@a.com")
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(alg);
        assertThat(live.validateToken(noVersion)).isNull();
        String otherIssuer = JWT.create().withIssuer("x").withSubject("ana@a.com").withClaim("ver", 4)
                .withExpiresAt(Instant.now().plusSeconds(60)).sign(alg);
        assertThat(live.validateToken(otherIssuer)).isNull();
    }
}

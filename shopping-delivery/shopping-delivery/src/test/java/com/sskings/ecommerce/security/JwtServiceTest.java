package com.sskings.ecommerce.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private static final String SECRET = "test-secret";
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "secret", SECRET);
    }

    @Test
    @DisplayName("Token gerado é válido e devolve o email como subject")
    void generateAndValidate_roundTrip() {
        String token = jwtService.generateToken("ana@a.com");
        assertThat(jwtService.validateToken(token)).isEqualTo("ana@a.com");
    }

    @Test
    @DisplayName("Token adulterado é rejeitado")
    void validate_tamperedToken_returnsNull() {
        String token = jwtService.generateToken("ana@a.com");
        assertThat(jwtService.validateToken(token + "x")).isNull();
    }

    @Test
    @DisplayName("Token assinado com outro segredo é rejeitado")
    void validate_wrongSecret_returnsNull() {
        String foreign = JWT.create().withIssuer("ecommerce-api").withSubject("ana@a.com")
                .sign(Algorithm.HMAC256("other-secret"));
        assertThat(jwtService.validateToken(foreign)).isNull();
    }

    @Test
    @DisplayName("Token expirado é rejeitado")
    void validate_expiredToken_returnsNull() {
        String expired = JWT.create().withIssuer("ecommerce-api").withSubject("ana@a.com")
                .withExpiresAt(Instant.now().minusSeconds(60))
                .sign(Algorithm.HMAC256(SECRET));
        assertThat(jwtService.validateToken(expired)).isNull();
    }

    @Test
    @DisplayName("Lixo no lugar do token é rejeitado")
    void validate_garbage_returnsNull() {
        assertThat(jwtService.validateToken("not-a-jwt")).isNull();
    }
}

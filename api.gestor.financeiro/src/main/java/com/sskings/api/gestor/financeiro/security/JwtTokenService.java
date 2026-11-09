package com.sskings.api.gestor.financeiro.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTCreationException;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
public class JwtTokenService {

    /** Segredos curtos ou que já foram públicos no repositório permitem forjar tokens (revisão de segurança 3). */
    static final java.util.Set<String> KNOWN_PUBLIC_SECRETS = java.util.Set.of("secret-key", "secret", "changeit");
    static final int MIN_SECRET_LENGTH = 32;

    private final String secret;

    public JwtTokenService(@Value("${security.jwt.secret}") String secret) {
        if (secret == null || secret.length() < MIN_SECRET_LENGTH || KNOWN_PUBLIC_SECRETS.contains(secret)) {
            throw new IllegalStateException("security.jwt.secret (JWT_SECRET) must be a random value with at least "
                    + MIN_SECRET_LENGTH + " characters (openssl rand -base64 48)");
        }
        this.secret = secret;
    }

    public String generateToken(UsuarioModel usuario){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            String token = JWT.create()
                    .withIssuer("gf-auth-api")
                    .withSubject(usuario.getUsername())
                    .withExpiresAt(generateExpiration())
                    .sign(algorithm);
            return token;
        }catch (JWTCreationException ex){
            throw new RuntimeException("Erro ao gerar token", ex);
        }
    }

    public String validateToken(String token){
        try {
            Algorithm algorithm = Algorithm.HMAC256(secret);
            return JWT.require(algorithm)
                    .withIssuer("gf-auth-api")
                    .build()
                    .verify(token)
                    .getSubject();
        }catch (JWTVerificationException ex){
            return "";
        }
    }

    private Instant generateExpiration(){
        return LocalDateTime.now()
                .plusHours(1)
                .toInstant(ZoneOffset.of("-03:00"));
    }
}

package com.sskings.ecommerce.security;

import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.sskings.ecommerce.model.User;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.temporal.ChronoUnit;

/**
 * JWT HS256 com issuer, expiração configurável e a versão de token do usuário ("ver"): quando
 * User.tokenVersion muda (logout, bloqueio, troca de papel, MFA), os tokens antigos deixam de valer.
 */
@Service
public class JwtService {

    static final String ISSUER = "ecommerce-api";
    static final String VERSION_CLAIM = "ver";

    private final Algorithm algorithm;
    private final long expirationMinutes;
    private final Clock clock;

    public JwtService(SecurityProperties props, Clock clock) {
        this.algorithm = Algorithm.HMAC256(props.jwt().secret());
        this.expirationMinutes = props.jwt().expirationMinutes();
        this.clock = clock;
    }

    public String generateToken(User user) {
        return JWT.create()
                .withIssuer(ISSUER)
                .withSubject(user.getEmail())
                .withClaim(VERSION_CLAIM, user.getTokenVersion())
                .withIssuedAt(clock.instant())
                .withExpiresAt(clock.instant().plus(expirationMinutes, ChronoUnit.MINUTES))
                .sign(algorithm);
    }

    /** Token válido → (email, versão); inválido, expirado ou adulterado → null. */
    public TokenClaims validateToken(String token) {
        try {
            DecodedJWT jwt = JWT.require(algorithm)
                    .withIssuer(ISSUER)
                    .withClaimPresence(VERSION_CLAIM)
                    .build()
                    .verify(token);
            return new TokenClaims(jwt.getSubject(), jwt.getClaim(VERSION_CLAIM).asInt());
        } catch (JWTVerificationException ex) {
            return null;
        }
    }

    public record TokenClaims(String email, int version) {}
}

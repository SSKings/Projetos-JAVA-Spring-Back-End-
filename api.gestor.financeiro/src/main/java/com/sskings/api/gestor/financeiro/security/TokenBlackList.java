package com.sskings.api.gestor.financeiro.security;


import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;

@Component
public class TokenBlackList {

    private final Set<String> revokedTokens = new HashSet<>();

    public void addRevokedToken(String token) {
        revokedTokens.add(token);
    }

    public boolean isTokenRevoked(String token) {
        return revokedTokens.contains(token);
    }
}
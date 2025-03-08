package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.usuario.AuthenticationDto;
import com.sskings.api.gestor.financeiro.dto.usuario.LoginResponseDto;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.security.JwtTokenService;
import com.sskings.api.gestor.financeiro.security.TokenBlackList;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.bind.annotation.*;


@RequiredArgsConstructor
@RestController
@RequestMapping("auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final TokenBlackList tokenBlackList;

    @PostMapping("/login")
    public ResponseEntity login(@RequestBody @Valid AuthenticationDto data){
        var usernamePassword = new UsernamePasswordAuthenticationToken(data.username(),data.password());
        var auth = this.authenticationManager.authenticate(usernamePassword);

        var token = jwtTokenService.generateToken((UsuarioModel) auth.getPrincipal());
        return ResponseEntity.ok(new LoginResponseDto(token));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity logout(@RequestHeader("Authorization") String authorizationHeader) {
        String token = authorizationHeader.substring(7); // Remove o prefixo "Bearer "
        tokenBlackList.addRevokedToken(token); // Adiciona o token à blacklist
        return ResponseEntity.noContent().build();
    }


}

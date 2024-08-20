package com.sskings.api.gestor.financeiro.dto.usuario;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;

import java.time.LocalDate;

public record UsuarioSimpleResponseDto(String username, String email, LocalDate dataDeCadastro) {

    public UsuarioSimpleResponseDto(UsuarioModel usuarioModel){
        this(usuarioModel.getUsername(), usuarioModel.getEmail(), usuarioModel.getDataCadastro());
    }
}

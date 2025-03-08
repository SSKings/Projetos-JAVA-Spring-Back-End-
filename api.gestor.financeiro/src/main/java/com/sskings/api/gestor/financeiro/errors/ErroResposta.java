package com.sskings.api.gestor.financeiro.errors;

import org.springframework.http.HttpStatus;

import java.util.List;


public record ErroResposta(int status,  String mensagem, List<ErroCampo> errors) {

    public static ErroResposta respostaPadrao(String mensagem){
        return new ErroResposta(HttpStatus.BAD_REQUEST.value(), mensagem, List.of() );
    }

    public static ErroResposta conflito(String mensagem){
        return new ErroResposta(HttpStatus.CONFLICT.value(), mensagem, List.of() );
    }

    public static  ErroResposta notFound(String mensagem){
        return new ErroResposta(HttpStatus.NOT_FOUND.value(), mensagem, List.of() );
    }
}

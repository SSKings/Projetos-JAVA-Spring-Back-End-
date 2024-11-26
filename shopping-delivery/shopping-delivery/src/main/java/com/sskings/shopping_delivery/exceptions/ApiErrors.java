package com.sskings.shopping_delivery.exceptions;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Setter
public class ApiErrors {

    private Integer statusCode;
    private LocalDateTime timestamp;
    private String message;
    private List<Problema> problemas;

    @AllArgsConstructor
    @Getter
    public static class Problema{
        private String nome;
        private String descricao;
    }
}

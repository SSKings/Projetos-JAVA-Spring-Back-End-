package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.errors.ErroCampo;
import com.sskings.api.gestor.financeiro.errors.ErroResposta;
import com.sskings.api.gestor.financeiro.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.stream.Collectors;

@RestControllerAdvice
public class ApplicationControllerAdvice {

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta handleBadRequestException(BadRequestException ex){
        return ErroResposta.respostaPadrao(ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErroResposta handleNotFoundException(NotFoundException ex){
        return ErroResposta.notFound(ex.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErroResposta handleConflictException(ConflictException ex){
        return ErroResposta.conflito(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ErroResposta handleMethodArgumentInvalidException(MethodArgumentNotValidException ex){
        List<ErroCampo> listaErrors = ex.getFieldErrors().stream().map(
                e -> new ErroCampo(e.getField(), e.getDefaultMessage())
        ).collect(Collectors.toList());

        return new ErroResposta(HttpStatus.UNPROCESSABLE_ENTITY.value(),"Erro de validação.", listaErrors);
    }

    @ExceptionHandler(SaldoContaException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta handleSaldoContaException(SaldoContaException ex){
        return ErroResposta.respostaPadrao(ex.getMessage());
    }

    @ExceptionHandler(ValorDeLancamentoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta handleValorDeLancamentoException(ValorDeLancamentoException ex){
        return ErroResposta.respostaPadrao(ex.getMessage());
    }

    @ExceptionHandler(LimiteCartaoException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErroResposta handleLimiteCartaoException(LimiteCartaoException ex){
        return ErroResposta.respostaPadrao(ex.getMessage());
    }
}   
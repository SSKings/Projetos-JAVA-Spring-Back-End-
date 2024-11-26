package com.sskings.shopping_delivery.exceptions;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiErrors.Problema> problemas = new ArrayList<>();

        for (ObjectError error : ex.getBindingResult().getAllErrors()) {
            String nome = ((FieldError)error).getField();
            String descricao = error.getDefaultMessage();
            problemas.add(new ApiErrors.Problema(nome,descricao));
        }

        ApiErrors errors = new ApiErrors();
        errors.setStatusCode(status.value());
        errors.setTimestamp(LocalDateTime.now());
        errors.setMessage("Um ou mais campos inválidos.");
        errors.setProblemas(problemas);
        return handleExceptionInternal(ex, errors, headers, status, request);
    }

    @ExceptionHandler(EmailExistenteException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiErrors handleEmailExistenteException(EmailExistenteException ex){
        ApiErrors apiErrors = new ApiErrors();
        apiErrors.setMessage(ex.getMessage());
        apiErrors.setStatusCode(HttpStatus.BAD_REQUEST.value());
        apiErrors.setTimestamp(LocalDateTime.now());
        return apiErrors;
    }
}

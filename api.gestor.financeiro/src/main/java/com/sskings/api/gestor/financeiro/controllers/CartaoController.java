package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.CartaoRequestDto;
import com.sskings.api.gestor.financeiro.dto.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.services.CartaoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/cartao")
public class CartaoController {

    private final CartaoService cartaoService;

    public CartaoController(CartaoService cartaoService) {
        this.cartaoService = cartaoService;
    }

    @PostMapping
    public ResponseEntity<CartaoResponseDto> save(@RequestBody CartaoRequestDto cartaoRequestDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(cartaoService.save(cartaoRequestDto));
    }
}

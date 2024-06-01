package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoRequestDto;
import com.sskings.api.gestor.financeiro.dto.cartao.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.models.CartaoModel;
import com.sskings.api.gestor.financeiro.services.CartaoService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/cartao")
public class CartaoController {

    private final CartaoService cartaoService;

    public CartaoController(CartaoService cartaoService) {
        this.cartaoService = cartaoService;
    }
    @GetMapping("/{id}")
    public ResponseEntity<Object> findById(@PathVariable(value = "id") UUID id){
        CartaoModel cartaoModel = cartaoService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,"Cartão não encontrado."));
        CartaoResponseDto response = new CartaoResponseDto(cartaoModel);
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PostMapping
    public ResponseEntity<CartaoResponseDto> save(@RequestBody CartaoRequestDto cartaoRequestDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(cartaoService.save(cartaoRequestDto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CartaoResponseDto> update(@PathVariable(value = "id") UUID id,
                                         @RequestBody CartaoRequestDto cartaoRequestDto){
        CartaoModel cartaoModel = cartaoService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cartão não encontrado."));
        BeanUtils.copyProperties(cartaoRequestDto, cartaoModel);
        return ResponseEntity.status(HttpStatus.OK).body(cartaoService.update(cartaoModel));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable(value = "id") UUID id){
        CartaoModel cartaoModel = cartaoService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cartão não encontrado."));
        cartaoService.delete(cartaoModel);
        return ResponseEntity.status(HttpStatus.OK).body("Cartão deletado.");
    }
}

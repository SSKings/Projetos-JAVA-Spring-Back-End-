package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoResponseDto;
import com.sskings.api.gestor.financeiro.services.LancamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/lancamentos")
@RequiredArgsConstructor
public class LancamentoController {

    private final LancamentoService lancamentoService;

    @PostMapping
    public ResponseEntity<LancamentoResponseDto> save(@RequestBody LancamentoRequestDto lancamentoRequestDto) {
        LancamentoResponseDto response = lancamentoService.save(lancamentoRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteById(@PathVariable(value = "id") UUID id){
        lancamentoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }


}

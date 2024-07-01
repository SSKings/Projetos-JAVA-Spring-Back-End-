package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.fonte.FonteResponseDto;
import com.sskings.api.gestor.financeiro.services.FonteLancamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/fonte")
@RequiredArgsConstructor
public class FonteLancamentoController {

    private final FonteLancamentoService fonteLancamentoService;

    @PostMapping
    public ResponseEntity<FonteResponseDto> save(@RequestBody FonteRequestDto fonteRequestDto){
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(fonteLancamentoService.save(fonteRequestDto));
    }

    @GetMapping
    public ResponseEntity<List<FonteResponseDto>> getAll(){
        return ResponseEntity.ok(fonteLancamentoService.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable UUID id){
        fonteLancamentoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

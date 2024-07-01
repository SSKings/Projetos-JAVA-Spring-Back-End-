package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.tipo.TipoResponseDto;
import com.sskings.api.gestor.financeiro.services.TipoLancamentoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/tipo")
@RequiredArgsConstructor
public class TipoLancamentoController {

    private final TipoLancamentoService tipoLancamentoService;

    @PostMapping
    public ResponseEntity<TipoResponseDto> save(@RequestBody TipoRequestDto tipoRequestDto) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(tipoLancamentoService.save(tipoRequestDto));
    }

    @GetMapping
    public ResponseEntity<List<TipoResponseDto>> findAll() {
        return ResponseEntity.ok(tipoLancamentoService.findAll());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable UUID id) {
        tipoLancamentoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

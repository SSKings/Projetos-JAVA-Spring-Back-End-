package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoRequestDto;
import com.sskings.api.gestor.financeiro.dto.lancamento.LancamentoResponseDto;
import com.sskings.api.gestor.financeiro.services.LancamentoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/lancamentos")
@RequiredArgsConstructor
public class LancamentoController {

    private final LancamentoService lancamentoService;

    @PostMapping
    public ResponseEntity<LancamentoResponseDto> save(@RequestBody @Valid LancamentoRequestDto lancamentoRequestDto) {
        LancamentoResponseDto response = lancamentoService.save(lancamentoRequestDto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<LancamentoResponseDto>> findAll(){
        return ResponseEntity.ok(lancamentoService.findAll());
    }

    @GetMapping("/card/all")
    public ResponseEntity<List<LancamentoResponseDto>> findAllCartaoLancamentosByParameters(
            @RequestParam(name = "valor", required = false) BigDecimal valor,
            @RequestParam(name = "data", required = false) LocalDate data
    ){

        List<LancamentoResponseDto> lancamentos = lancamentoService
                .findAllCartaoLancamentosByParameters(valor, data);

        return ResponseEntity.ok(lancamentos);
    }

    @GetMapping("/account/all")
    public ResponseEntity<List<LancamentoResponseDto>> findAllContaLancamentosByParameters(
            @RequestParam(name = "valor", required = false) BigDecimal valor,
            @RequestParam(name = "data", required = false) LocalDate data
    ){

        List<LancamentoResponseDto> lancamentos = lancamentoService
                .findAllContaLancamentosByParameters(valor, data);

        return ResponseEntity.ok(lancamentos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioId(@PathVariable(value = "id") UUID id){
        return ResponseEntity.ok(lancamentoService.findByUsuarioId(id));
    }

    @GetMapping("/fonte")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioIdAndFonteNomeIgnoreCase(
            @RequestParam("u") UUID id,
            @RequestParam("f") String fonte
    ){
        return ResponseEntity.ok(lancamentoService.findByUsuarioIdAndFonteNomeIgnoreCase(id,fonte));
    }

    @GetMapping("/tipo")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioIdAndTipoNomeIgnoreCase(
            @RequestParam("u") UUID id,
            @RequestParam("t") String tipo
    ){
        return ResponseEntity.ok(lancamentoService.findByUsuarioIdAndTipoNomeIgnoreCase(id,tipo));
    }

    @GetMapping("/data")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioIdAndDataLancamento(
            @RequestParam("u") UUID id,
            @RequestParam("d")LocalDate data
            ){
        return ResponseEntity.ok(lancamentoService.findByUsuarioIdAndDataLancamento(id,data));
    }

    @GetMapping("/intervalo")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioIdAndDataLancamentoBetween(
            @RequestParam("u") UUID id,
            @RequestParam("d1") LocalDate data1,
            @RequestParam("d2") LocalDate data2
    ){
        return ResponseEntity.ok(lancamentoService.findByUsuarioIdAndDataLancamentoBetween(id,data1,data2));
    }


    @GetMapping("/valor")
    public ResponseEntity<List<LancamentoResponseDto>> findByUsuarioIdAndValor(
            @RequestParam("u") UUID id,
            @RequestParam("v") BigDecimal valor
    ){
        return ResponseEntity.ok(lancamentoService.findByUsuarioIdAndValor(id,valor));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteById(@PathVariable(value = "id") UUID id){
        lancamentoService.deleteById(id);
        return ResponseEntity.noContent().build();
    }


}

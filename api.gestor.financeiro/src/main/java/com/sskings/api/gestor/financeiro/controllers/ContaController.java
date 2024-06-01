package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.conta.ContaRequestDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import com.sskings.api.gestor.financeiro.models.ContaModel;
import com.sskings.api.gestor.financeiro.services.ContaService;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/contas")
public class ContaController {


    private final ContaService contaService;

    public ContaController(ContaService contaService) {
        this.contaService = contaService;
    }

    @PostMapping
    public ResponseEntity<ContaResponseDto> save(@RequestBody ContaRequestDto contaRequestDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(contaService.save(contaRequestDto));
    }


    @GetMapping("/{id}")
    public ResponseEntity<ContaResponseDto> findById(@PathVariable(value = "id") UUID id){
        Optional<ContaModel> contaOptional = contaService.findById(id);
        ContaResponseDto response = new ContaResponseDto(contaOptional.get());
        return ResponseEntity.status(HttpStatus.OK).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable(value = "id") UUID id,
                                                   @RequestBody ContaRequestDto contaRequestDto){
        ContaModel contaModel = contaService.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conta não encontrada."));
        BeanUtils.copyProperties(contaRequestDto, contaModel);
        ContaResponseDto response = contaService.update(contaModel);
        return ResponseEntity.status(HttpStatus.OK).body(response);


    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable(value = "id") UUID id){
        Optional<ContaModel> contaOptional = contaService.findById(id);
        if (!contaOptional.isPresent()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Conta não encontrada.");
        }
        contaService.delete(contaOptional.get());
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }
}

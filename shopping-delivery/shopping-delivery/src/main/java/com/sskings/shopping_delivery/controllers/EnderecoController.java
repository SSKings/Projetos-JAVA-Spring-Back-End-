package com.sskings.shopping_delivery.controllers;

import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.services.EnderecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/enderecos")
public class EnderecoController {

    private final EnderecoService enderecoService;

    @PostMapping
    public ResponseEntity<EnderecoModel> salvarEndereco(@RequestBody EnderecoModel enderecoModel) {
        return ResponseEntity.ok(enderecoService.salvar(enderecoModel));
    }

    @GetMapping
    public ResponseEntity<List<EnderecoModel>> listarEnderecos() {
        return ResponseEntity.ok(enderecoService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<EnderecoModel> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(enderecoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EnderecoModel> atualizarEndereco(@PathVariable Long id,
                                                           @RequestBody EnderecoModel enderecoModel) {
        EnderecoModel enderecoAtualizado = enderecoService.atualizar(id, enderecoModel);
        return ResponseEntity.ok(enderecoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarEndereco(@PathVariable Long id) {
        buscarPorId(id);
        return ResponseEntity.noContent().build();        
    }
}

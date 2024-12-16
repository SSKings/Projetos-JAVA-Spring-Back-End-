package com.sskings.shopping_delivery.controllers;

import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.services.ClienteService;
import lombok.AllArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@AllArgsConstructor
public class ClienteController {

    private ClienteService clienteService;

    @PostMapping
    public ResponseEntity<ClienteModel> salvarCliente(@RequestBody ClienteModel cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.salvar(cliente));
    }

    @GetMapping
    public ResponseEntity<List<ClienteModel>> ListarClientes(){
        return ResponseEntity.status(HttpStatus.OK).body(clienteService.listar());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteModel> buscarPorId(@PathVariable long id) {
        ClienteModel clienteRetornado = clienteService.buscarPorId(id);
        return ResponseEntity.ok(clienteRetornado);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteModel> atualizarCliente(@PathVariable long id, @RequestBody ClienteModel cliente) {
        return ResponseEntity.status(HttpStatus.OK).body(clienteService.atualizar(id, cliente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerCliente(@PathVariable long id) {
        clienteService.buscarPorId(id);
        return ResponseEntity.noContent().build();
    }
}

package com.sskings.shopping_delivery.controllers;

import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.services.ClienteService;
import com.sskings.shopping_delivery.services.EnderecoService;
import com.sskings.shopping_delivery.services.PedidoService;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/clientes")
@AllArgsConstructor
public class ClienteController {

    private ClienteService clienteService;
    private EnderecoService enderecoService;
    private PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<ClienteModel> salvarCliente(@Valid @RequestBody ClienteModel cliente) {
        return ResponseEntity.status(HttpStatus.CREATED).body(clienteService.salvar(cliente));
    }

    @GetMapping
    public ResponseEntity<Page<ClienteModel>> listarClientes(
            @PageableDefault(size = 10, sort = "nome") Pageable pageable) {
        return ResponseEntity.ok(clienteService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClienteModel> buscarPorId(@PathVariable long id) {
        return ResponseEntity.ok(clienteService.buscarPorId(id));
    }

    @GetMapping("/{id}/enderecos")
    public ResponseEntity<List<EnderecoModel>> listarEnderecosPorCliente(@PathVariable Long id) {
        clienteService.buscarPorId(id);
        return ResponseEntity.ok(enderecoService.listarPorClienteId(id));
    }

    @GetMapping("/{id}/pedidos")
    public ResponseEntity<List<PedidoModel>> listarPedidosPorCliente(@PathVariable Long id) {
        clienteService.buscarPorId(id);
        return ResponseEntity.ok(pedidoService.listarPorClienteId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClienteModel> atualizarCliente(@PathVariable long id,
                                                          @Valid @RequestBody ClienteModel cliente) {
        return ResponseEntity.ok(clienteService.atualizar(id, cliente));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerCliente(@PathVariable long id) {
        clienteService.removerPorId(id);
        return ResponseEntity.noContent().build();
    }
}

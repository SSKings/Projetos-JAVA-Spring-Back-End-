package com.sskings.shopping_delivery.controllers;

import com.sskings.shopping_delivery.models.PedidoModel;
import com.sskings.shopping_delivery.models.StatusPedido;
import com.sskings.shopping_delivery.services.PedidoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/pedidos")
@RequiredArgsConstructor
public class PedidoController {

    private final PedidoService pedidoService;

    @PostMapping
    public ResponseEntity<PedidoModel> salvarPedido(@Valid @RequestBody PedidoModel pedido) {
        return ResponseEntity.status(HttpStatus.CREATED).body(pedidoService.salvar(pedido));
    }

    @GetMapping
    public ResponseEntity<Page<PedidoModel>> listarPedidos(
            @PageableDefault(size = 10, sort = "dataPedido") Pageable pageable) {
        return ResponseEntity.ok(pedidoService.listar(pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PedidoModel> buscarPorId(@PathVariable Long id) {
        return ResponseEntity.ok(pedidoService.buscarPorId(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<PedidoModel> atualizarPedido(@PathVariable Long id, 
                                                       @Valid @RequestBody PedidoModel pedido) {
        return ResponseEntity.ok(pedidoService.atualizar(id, pedido));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<PedidoModel> atualizarStatus(@PathVariable Long id, 
                                                       @RequestParam StatusPedido status) {
        return ResponseEntity.ok(pedidoService.atualizarStatus(id, status));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> removerPedido(@PathVariable Long id) {
        pedidoService.removerPorId(id);
        return ResponseEntity.noContent().build();
    }
}

package com.sskings.shopping_delivery.controllers;

import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.services.EnderecoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/enderecos")
public class EnderecoController {

    private final EnderecoService enderecoService;

    @PostMapping
    public ResponseEntity<EnderecoModel> salvarEndereco(@RequestBody EnderecoModel enderecoModel) {
        return ResponseEntity.ok(enderecoService.salvar(enderecoModel));
    }
}

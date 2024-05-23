package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.services.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/usuario")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }

    @PostMapping
    public ResponseEntity<Object> save(@RequestBody UsuarioModel usuario){
        if(usuarioService.existsByEmail(usuario.getEmail())){
            return ResponseEntity.status(HttpStatus.CONFLICT).body("Este e-mail já está em uso.");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.save(usuario));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioModel>> findAll(){
        return ResponseEntity.status(HttpStatus.OK).body(usuarioService.findAll());
    }

    @GetMapping("/{param}")
    public ResponseEntity<List<UsuarioModel>> findByNomeIgnoreCaseContaining(
            @PathVariable(value = "param") String param){
        return ResponseEntity.status(HttpStatus.FOUND).body(usuarioService.findByNomeIgnoreCaseContaining(param));
    }

}

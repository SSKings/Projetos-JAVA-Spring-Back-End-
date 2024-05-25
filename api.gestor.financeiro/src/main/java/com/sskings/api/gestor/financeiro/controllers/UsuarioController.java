package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.services.UsuarioService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/usuarios")
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

    @GetMapping("/{id}")
    public ResponseEntity<Object> findById(@PathVariable(value = "id") UUID id){
        Optional<UsuarioModel> usuarioOptional = usuarioService.findById(id);
        if (!usuarioOptional.isPresent()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
        }
        return ResponseEntity.status(HttpStatus.OK).body(usuarioOptional.get());
    }
    @GetMapping("/s/{param}")
    public ResponseEntity<List<UsuarioModel>> findByNomeIgnoreCaseContaining(
            @PathVariable(value = "param") String param){
        return ResponseEntity.status(HttpStatus.FOUND).body(usuarioService.findByNomeIgnoreCaseContaining(param));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable(value = "id") UUID id,
                                         @RequestBody UsuarioModel usuario){
        Optional<UsuarioModel> usuarioOptional = usuarioService.findById(id);
        if(!usuarioOptional.isPresent()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
        }
        usuario.setId(usuarioOptional.get().getId());
        usuarioService.update(usuario);
        return ResponseEntity.status(HttpStatus.OK).body("Alterações realizadas.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> delete(@PathVariable(value = "id") UUID id){
        Optional<UsuarioModel> usuarioOptional = usuarioService.findById(id);
        if (!usuarioOptional.isPresent()){
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuário não encontrado.");
        }
        UsuarioModel usuario = usuarioOptional.get();
        usuarioService.delete(usuario);
        return ResponseEntity.status(HttpStatus.OK).body("Usuário deletado");
    }
}

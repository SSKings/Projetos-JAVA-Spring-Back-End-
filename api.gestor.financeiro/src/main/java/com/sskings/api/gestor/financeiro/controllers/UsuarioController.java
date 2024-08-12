package com.sskings.api.gestor.financeiro.controllers;

import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioRequestDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioResponseDto;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.services.UsuarioService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("api/usuarios")
public class UsuarioController {

    private final UsuarioService usuarioService;

    public UsuarioController(UsuarioService usuarioService) {
        this.usuarioService = usuarioService;
    }


    @PostMapping("/register")
    public ResponseEntity<Object> save(@RequestBody @Valid UsuarioRequestDto usuarioRequestDto){
        return ResponseEntity.status(HttpStatus.CREATED).body(usuarioService.save(usuarioRequestDto));
    }

    @GetMapping
    public ResponseEntity<List<UsuarioModel>> findAll(){
        return ResponseEntity.status(HttpStatus.OK).body(usuarioService.findAll());
    }

    @GetMapping("/p")
    public ResponseEntity<Page<UsuarioModel>> findUsuarioWithPagination(@RequestParam(defaultValue = "0") int page,
                                                                        @RequestParam(defaultValue = "1") int size){
            return ResponseEntity.ok(usuarioService.findUsuarioWithPagination(page,size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Object> findById(@PathVariable(value = "id") UUID id){
        return ResponseEntity.ok(usuarioService.findById(id));
    }
    
    @GetMapping("/s/{param}")
    public ResponseEntity<List<UsuarioModel>> findByNomeIgnoreCaseContaining(
            @PathVariable(value = "param") String param){
        return ResponseEntity.status(HttpStatus.FOUND).body(usuarioService.findByNomeIgnoreCaseContaining(param));
    }

    @GetMapping("/cartoes/{id}")
    public ResponseEntity<UsuarioResponseDto> findByIdWithCartoesAndContas(@PathVariable(value = "id") UUID id){
            return ResponseEntity.ok(usuarioService.findByIdWithCartoesAndContas(id));
    }

    @GetMapping("/lancamentos/{id}")
    public ResponseEntity<UsuarioResponseDto> findByIdWithLancamentos(@PathVariable(value = "id") UUID id){
        return ResponseEntity.ok(usuarioService.findByIdWithLancamentos(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Object> update(@PathVariable(value = "id") UUID id,
                                         @RequestBody UsuarioModel usuarioModel){
        usuarioService.update(id, usuarioModel);
        return ResponseEntity.status(HttpStatus.OK).body("Alterações realizadas.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Object> deleteById(@PathVariable(value = "id") UUID id){
        usuarioService.deleteById(id);
        return ResponseEntity.status(HttpStatus.OK).body("Usuário deletado");
    }
}

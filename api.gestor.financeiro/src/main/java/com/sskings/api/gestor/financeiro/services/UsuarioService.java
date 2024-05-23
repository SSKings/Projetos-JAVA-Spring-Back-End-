package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    @Transactional
    public UsuarioModel save(UsuarioModel usuario){
        return usuarioRepository.save(usuario);
    }

    public List<UsuarioModel> findAll(){
        return usuarioRepository.findAll();
    }

    public Optional<UsuarioModel> findById(UUID id){
        return usuarioRepository.findById(id);
    }

    @Transactional
    public void delete(UsuarioModel usuario){
        usuarioRepository.delete(usuario);
    }

    public boolean existsByEmail(String email){
        return usuarioRepository.existsByEmail(email);
    }

}

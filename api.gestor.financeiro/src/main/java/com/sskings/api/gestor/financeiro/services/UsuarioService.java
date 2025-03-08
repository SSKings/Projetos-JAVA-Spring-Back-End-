package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioRequestDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioResponseDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioSimpleResponseDto;
import com.sskings.api.gestor.financeiro.exception.ConflictException;
import com.sskings.api.gestor.financeiro.exception.NotFoundException;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import com.sskings.api.gestor.financeiro.utils.Utils;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UsuarioModel save(UsuarioRequestDto usuarioRequestDto){

        if(existsByEmail(usuarioRequestDto.email())){
            throw new ConflictException("O e-mail já está cadastrado");
        }
        if(existsByUsername(usuarioRequestDto.username())){
            throw new ConflictException("Nome de usuário não disponível.");
        }
        var usuario = new UsuarioModel(usuarioRequestDto);
        String senhaCriptografada = passwordEncoder.encode(usuarioRequestDto.password());
        usuario.setPassword(senhaCriptografada);
        return usuarioRepository.save(usuario);

    }

    public List<UsuarioSimpleResponseDto> findAll(){
        List<UsuarioModel> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()){
            return new ArrayList<>();
        }
        return usuarios.stream()
                .map(UsuarioSimpleResponseDto::new)
                .toList();
    }

    public Page<UsuarioSimpleResponseDto> findUsuarioWithPagination(int page, int size){
        Pageable pageable = PageRequest.of(page,size);
        Page<UsuarioSimpleResponseDto> usuarios = usuarioRepository.findAll(pageable)
                .map(UsuarioSimpleResponseDto::new);
        if (usuarios.isEmpty()){
            throw new NotFoundException("não há usuários cadastrados.");
        }
        return usuarios;

    }

    public UsuarioSimpleResponseDto findById(UUID id){
        return usuarioRepository.findById(id)
                .map(UsuarioSimpleResponseDto::new)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    @Transactional
    public UsuarioModel update(UUID id, UsuarioModel usuario){
        UsuarioModel usuarioModel = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        usuario.setId(usuarioModel.getId());
        return usuarioRepository.save(usuario);
    }

    @Transactional
    public void delete(UsuarioModel usuario){
        usuarioRepository.delete(usuario);
    }

    public boolean existsByEmail(String email){
        return usuarioRepository.existsByEmail(email);
    }

    public boolean existsByUsername(String username){ return usuarioRepository.existsByUsername(username);}


    public List<UsuarioModel> findByNomeIgnoreCaseContaining(String nome){
        List<UsuarioModel> usuarios = usuarioRepository.findByUsernameIgnoreCaseContaining(nome);
        if (usuarios.isEmpty()){
            return new ArrayList<>();
        }
        return usuarios;
    }

    public UsuarioResponseDto findByIdWithCartoes(UUID id){
        return usuarioRepository.findByIdWithCartoes(id)
                .map(usuarioModel -> UsuarioResponseDto
                        .builder()
                            .nome(usuarioModel.getUsername())
                            .email(usuarioModel.getEmail())
                            .cartoes(Utils.convertCartoes(usuarioModel.getCartoes()))
                            .contas(new HashSet<>())
                            .lancamentos(new HashSet<>())
                .build())
                .orElseThrow(() -> new NotFoundException("Usuario não encontrado"));
    }

    public UsuarioResponseDto findByIdWithContas(UUID id){
        return usuarioRepository.findByIdWithContas(id)
                .map(usuarioModel -> UsuarioResponseDto
                        .builder()
                            .nome(usuarioModel.getUsername())
                            .email(usuarioModel.getEmail())
                            .cartoes(new HashSet<>())
                            .contas(Utils.convertContas(usuarioModel.getContas()))
                            .lancamentos(new HashSet<>())
                        .build())
                .orElseThrow(() -> new NotFoundException("Usuario não encontrado"));
    }
    public UsuarioResponseDto findByIdWithCartoesAndContas(UUID id){
        return usuarioRepository.findByIdWithCartoesAndContas(id)
                .map(usuarioModel -> UsuarioResponseDto
                        .builder()
                            .nome(usuarioModel.getUsername())
                            .email(usuarioModel.getEmail())
                            .cartoes(Utils.convertCartoes(usuarioModel.getCartoes()))
                            .contas(Utils.convertContas(usuarioModel.getContas()))
                            .lancamentos(new HashSet<>())
                .build())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    public UsuarioResponseDto findByIdWithLancamentos(UUID id){
        return usuarioRepository.findByIdWithLancamentos(id)
                .map(usuarioModel -> UsuarioResponseDto
                        .builder()
                            .nome(usuarioModel.getUsername())
                            .cartoes(new HashSet<>())
                            .contas(new HashSet<>())
                            .lancamentos(Utils.converterLancamentos(usuarioModel.getLancamentos()))
                .build())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    @Transactional
    public void deleteById(UUID id) {
        UsuarioModel usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        usuarioRepository.delete(usuario);
    }

}

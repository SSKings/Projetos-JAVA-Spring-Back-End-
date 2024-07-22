package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioRequestDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioResponseDto;
import com.sskings.api.gestor.financeiro.exception.ConflictException;
import com.sskings.api.gestor.financeiro.exception.NotFoundException;
import com.sskings.api.gestor.financeiro.models.CartaoModel;
import com.sskings.api.gestor.financeiro.models.ContaModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;


    @Transactional
    public UsuarioModel save(UsuarioRequestDto usuarioRequestDto){
        if(usuarioRepository.existsByEmail(usuarioRequestDto.email())){
            throw new ConflictException("O e-mail já está cadastrado");
        }
        var usuario = new UsuarioModel(usuarioRequestDto);
        String senhaCriptografada = passwordEncoder.encode(usuarioRequestDto.password());
        usuario.setPassword(senhaCriptografada);
        usuario.setDataCadastro(LocalDate.now(ZoneId.of("America/Sao_Paulo")));
        return usuarioRepository.save(usuario);

    }

    public List<UsuarioModel> findAll(){
        List<UsuarioModel> usuarios = usuarioRepository.findAll();
        if (usuarios.isEmpty()){
            throw new NotFoundException("não há usuários cadastrados.");
        }
        return usuarios;
    }

    public UsuarioModel findById(UUID id){
        return usuarioRepository.findById(id)
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


    public List<UsuarioModel> findByNomeIgnoreCaseContaining(String nome){
        return usuarioRepository.findByUsernameIgnoreCaseContaining(nome);
    }

    public UsuarioResponseDto findByIdWithCartoes(UUID id){
        return usuarioRepository.findByIdWithCartoesAndContas(id).map(usuarioModel -> UsuarioResponseDto.builder()
                .nome(usuarioModel.getUsername())
                .email(usuarioModel.getEmail())
                .cartoes(convertCartoes(usuarioModel.getCartoes()))
                .contas(convertContas(usuarioModel.getContas())).build())
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
    }

    @Transactional
    public void deleteById(UUID id) {
        UsuarioModel usuario = usuarioRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Usuário não encontrado."));
        usuarioRepository.delete(usuario);
    }

    private Set<CartaoResponseDto> convertCartoes(Set<CartaoModel> cartoes){
        if(CollectionUtils.isEmpty(cartoes)){
            return Collections.emptySet();
        }
        return cartoes.stream()
                .map(CartaoResponseDto::new).collect(Collectors.toSet());
    }

    private Set<ContaResponseDto> convertContas(Set<ContaModel> contas){
        if(CollectionUtils.isEmpty(contas)){
            return  Collections.emptySet();
        }
        return contas.stream()
                .map(ContaResponseDto::new).collect(Collectors.toSet());
    }

}

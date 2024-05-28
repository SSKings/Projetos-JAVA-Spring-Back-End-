package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.ContaRequestDto;
import com.sskings.api.gestor.financeiro.dto.ContaResponseDto;
import com.sskings.api.gestor.financeiro.models.ContaModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.ContaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository contaRepository;
    private final UsuarioService usuarioService;

    @Transactional
    public ContaResponseDto save(ContaRequestDto contaRequestDto){
        UsuarioModel usuario = usuarioService.findById(contaRequestDto.usuario_id())
                .orElseThrow(() -> new RuntimeException("Código de usuário inválido."));
        ContaModel contaModel    = new ContaModel(contaRequestDto);
        contaModel.setUsuario(usuario);
        contaRepository.save(contaModel);
        return new ContaResponseDto(contaModel);
    }

    public Optional<ContaModel> findById(UUID id){
        return contaRepository.findById(id);
    }

    @Transactional
    public ContaResponseDto update(ContaRequestDto contaRequestDto){
        UsuarioModel usuario = usuarioService.findById(contaRequestDto.usuario_id())
                .orElseThrow(() -> new RuntimeException("Usuário inválido."));
        ContaModel conta = new ContaModel(contaRequestDto);
        conta.setUsuario(usuario);
        contaRepository.save(conta);
        ContaResponseDto dto = new ContaResponseDto(conta);
        return dto;
    }

    @Transactional
    public void delete(ContaModel conta){
        contaRepository.delete(conta);
    }

}

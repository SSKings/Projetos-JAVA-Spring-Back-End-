package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.conta.ContaRequestDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import com.sskings.api.gestor.financeiro.exception.BadRequestException;
import com.sskings.api.gestor.financeiro.models.ContaModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.ContaRepository;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ContaService {

    private final ContaRepository contaRepository;
    private final UsuarioRepository usuarioRepository;

    @Transactional
    public ContaResponseDto save(ContaRequestDto contaRequestDto){
        UsuarioModel usuario = usuarioRepository.findById(contaRequestDto.usuario_id())
                .orElseThrow(() -> new BadRequestException("Código de usuário inválido."));
        ContaModel contaModel = new ContaModel(contaRequestDto);
        contaModel.setUsuario(usuario);
        contaRepository.save(contaModel);
        return new ContaResponseDto(contaModel);
    }

    public Optional<ContaModel> findById(UUID id){
        return contaRepository.findById(id);
    }

    @Transactional
    public ContaResponseDto update(ContaModel contaModel){
        UsuarioModel usuario = usuarioRepository.findById(contaModel.getUsuario().getId())
                .orElseThrow(() -> new BadRequestException("Usuário inválido."));
        contaModel.setUsuario(usuario);
        contaRepository.save(contaModel);
        return new ContaResponseDto(contaModel);
    }

    @Transactional
    public void delete(ContaModel conta){
        contaRepository.delete(conta);
    }

}

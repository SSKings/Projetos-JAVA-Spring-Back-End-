package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.conta.ContaRequestDto;
import com.sskings.api.gestor.financeiro.dto.conta.ContaResponseDto;
import com.sskings.api.gestor.financeiro.exception.RegraNegocioException;
import com.sskings.api.gestor.financeiro.models.ContaModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.ContaRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
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
                .orElseThrow(() -> new RegraNegocioException("Código de usuário inválido."));
        ContaModel contaModel    = new ContaModel(contaRequestDto);
        contaModel.setUsuario(usuario);
        contaRepository.save(contaModel);
        return new ContaResponseDto(contaModel);
    }

    public Optional<ContaModel> findById(UUID id){
        return contaRepository.findById(id);
    }

    @Transactional
    public ContaResponseDto update(ContaModel contaModel){
        UsuarioModel usuario = usuarioService.findById(contaModel.getUsuario().getId())
                .orElseThrow(() -> new RegraNegocioException("Usuário inválido."));
        contaModel.setUsuario(usuario);
        contaRepository.save(contaModel);
        ContaResponseDto dto = new ContaResponseDto(contaModel);
        return dto;
    }

    @Transactional
    public void delete(ContaModel conta){
        contaRepository.delete(conta);
    }

}

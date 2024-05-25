package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.ContaRequestDto;
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

    public final ContaRepository contaRepository;
    public final UsuarioService usuarioService;

    @Transactional
    public ContaModel save(ContaRequestDto contaRequestDto){
        UsuarioModel usuario = usuarioService.findById(contaRequestDto.usuario_id())
                .orElseThrow(() -> new RuntimeException("Código de usuário inválido."));
        ContaModel contaModel = new ContaModel(contaRequestDto);
        contaModel.setUsuario(usuario);
        return contaRepository.save(contaModel);
    }

    public Optional<ContaModel> findById(UUID id){
        return contaRepository.findById(id);
    }
    @Transactional
    public void delete(ContaModel conta){
        contaRepository.delete(conta);
    }

}

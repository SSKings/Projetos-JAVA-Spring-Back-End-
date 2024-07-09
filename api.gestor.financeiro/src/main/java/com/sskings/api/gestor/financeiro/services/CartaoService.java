package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.cartao.CartaoRequestDto;
import com.sskings.api.gestor.financeiro.dto.cartao.CartaoResponseDto;
import com.sskings.api.gestor.financeiro.exception.BadRequestException;
import com.sskings.api.gestor.financeiro.models.CartaoModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.repositories.CartaoRepository;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CartaoService {

    private final UsuarioRepository usuarioRepository;
    private final CartaoRepository cartaoRepository;

    @Transactional
    public CartaoResponseDto save(CartaoRequestDto cartaoRequestDto){
        UsuarioModel usuario = usuarioRepository.findById(cartaoRequestDto.usuario_id())
                .orElseThrow(() -> new BadRequestException("Código de usuário inválido"));
        CartaoModel cartaoModel = new CartaoModel(cartaoRequestDto);
        cartaoModel.setUsuario(usuario);
        cartaoRepository.save(cartaoModel);
        return new CartaoResponseDto(cartaoModel);
    }


    public Optional<CartaoModel> findById(UUID id){
        return cartaoRepository.findById(id);
    }

    public List<CartaoModel> findByUsuarioId(UUID id){
        return cartaoRepository.findByUsuarioId(id);
    }

    @Transactional
    public CartaoResponseDto update(CartaoModel cartaoModel){
        UsuarioModel usuario = usuarioRepository.findById(cartaoModel.getUsuario().getId())
                .orElseThrow(() -> new BadRequestException("Código de usuário inválido."));
        cartaoModel.setUsuario(usuario);
        cartaoRepository.save(cartaoModel);
        return new CartaoResponseDto(cartaoModel);
    }

    @Transactional
    public void delete(CartaoModel cartaoModel){
        cartaoRepository.delete(cartaoModel);
    }
}

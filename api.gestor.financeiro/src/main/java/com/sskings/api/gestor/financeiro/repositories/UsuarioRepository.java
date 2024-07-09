package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioModel, UUID> {

    boolean existsByEmail(String email);

    List<UsuarioModel> findByNomeIgnoreCaseContaining(String nome);

    @Query(" SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.cartoes LEFT JOIN FETCH u.contas WHERE u.id = :id ")
    Optional<UsuarioModel> findByIdWithCartoesAndContas(@Param("id") UUID id);

}
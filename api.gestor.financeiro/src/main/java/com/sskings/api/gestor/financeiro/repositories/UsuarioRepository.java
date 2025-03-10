package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.CartaoModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioModel, UUID> {

    boolean existsByEmail(String email);

    boolean existsByUsername(String username);

    List<UsuarioModel> findByUsernameIgnoreCaseContaining(String username);

    @Query(" SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.cartoes WHERE u.id = :id ")
    Optional<UsuarioModel> findByIdWithCartoes(@Param("id") UUID id);

    @Query(" SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.contas WHERE u.id = :id ")
    Optional<UsuarioModel> findByIdWithContas(@Param("id") UUID id);

    @Query(" SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.cartoes LEFT JOIN FETCH u.contas WHERE u.id = :id ")
    Optional<UsuarioModel> findByIdWithCartoesAndContas(@Param("id") UUID id);

    @Query("SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.lancamentos WHERE u.id = :id")
    Optional<UsuarioModel> findByIdWithLancamentos(@Param("id") UUID id);

    Optional<UserDetails> findByUsername(String username);

}
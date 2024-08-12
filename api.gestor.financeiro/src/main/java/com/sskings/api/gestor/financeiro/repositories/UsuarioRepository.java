package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    List<UsuarioModel> findByUsernameIgnoreCaseContaining(String username);

    @Query(" SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.cartoes LEFT JOIN FETCH u.contas WHERE u.id = :id ")
    Optional<UsuarioModel> findByIdWithCartoesAndContas(@Param("id") UUID id);

    @Query("SELECT u FROM UsuarioModel u LEFT JOIN FETCH u.lancamentos WHERE u.id = :id")
    UsuarioModel findByIdWithLancamentos(@Param("id") UUID id);

    UserDetails findByUsername(String username);
}
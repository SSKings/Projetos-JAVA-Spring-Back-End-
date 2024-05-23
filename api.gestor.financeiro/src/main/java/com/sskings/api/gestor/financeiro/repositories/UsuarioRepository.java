package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface UsuarioRepository extends JpaRepository<UsuarioModel, UUID> {

    boolean existsByEmail(String email);
}

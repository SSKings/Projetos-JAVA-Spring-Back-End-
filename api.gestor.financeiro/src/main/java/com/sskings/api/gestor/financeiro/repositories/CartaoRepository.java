package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.CartaoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CartaoRepository extends JpaRepository<CartaoModel, UUID> {

}

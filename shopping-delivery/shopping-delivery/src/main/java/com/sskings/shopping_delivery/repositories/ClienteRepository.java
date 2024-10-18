package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.ClienteModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ClienteRepository extends JpaRepository<ClienteModel, Long> {

    Optional<ClienteModel> findByNome(String nome);

    Optional<ClienteModel> findByCpf(String cpf);

    boolean existsByEmail(String email);

    @Query("SELECT c FROM ClienteModel c JOIN FETCH c.enderecos WHERE c.id = :id")
    Optional<ClienteModel> findByIdWithEnderecos(@Param("id") Long id);

    @Query("SELECT c FROM ClienteModel c JOIN FETCH c.pedidos WHERE c.id = :id")
    Optional<ClienteModel> findByIdWithPedidos(@Param("id") Long id);

}

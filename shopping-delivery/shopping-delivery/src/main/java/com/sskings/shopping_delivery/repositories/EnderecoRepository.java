package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.EnderecoModel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EnderecoRepository extends JpaRepository<EnderecoModel, Long> {

    List<EnderecoModel> findByCliente_Id(Long clienteId);
}

package com.sskings.shopping_delivery.repositories;

import com.sskings.shopping_delivery.models.ClienteModel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClienteRepository extends JpaRepository<ClienteModel, Long> {
}

package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.CartaoModel;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.models.UsuarioRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;

@DataJpaTest
class UsuarioRepositoryTest {

        @Autowired
        private UsuarioRepository usuarioRepository;

        @Autowired
        private TestEntityManager entityManager;

        private UsuarioModel usuario0;

        @BeforeEach
        public void setup() {

                usuario0 = new UsuarioModel(
                        null,
                        "sergio",
                        "123",
                        UsuarioRole.ADMIN,
                        "sergio@email.com",
                        LocalDate.now(),
                        null,
                        null,
                        null
                );



                CartaoModel cartao0 = new CartaoModel(
                        null,
                        12345678L,
                        "NU",
                        LocalDate.now(),
                        usuario0,
                        new BigDecimal(15000),
                        new BigDecimal(15000));

                usuario0.setCartoes(Set.of(cartao0));

                entityManager.persist(usuario0);
                entityManager.persist(cartao0);
                entityManager.flush();
        }


        // test[System Under Test]_[Condition or State Change]_[Expected Result]
        @DisplayName("givenUsuarioId whenFindByIdWithCartoes thenReturnUsuarioWithCartoes")
        @Test
        void givenUsuarioId_whenFindByIdWithCartoes_thenReturnUsuarioWithCartoes() {
                // Given / Arrange

                // When / Act
                Optional<UsuarioModel> usuarioOptional = usuarioRepository.findByIdWithCartoes(usuario0.getId());

                // Then / Assert
                Assertions.assertTrue(usuarioOptional.isPresent());
                Assertions.assertNotNull(usuarioOptional.get().getCartoes());


        }

}
package com.sskings.api.gestor.financeiro.repositories;

import com.sskings.api.gestor.financeiro.models.*;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import javax.swing.text.html.Option;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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
                        null);

                CartaoModel cartao0 = new CartaoModel(
                        null,
                        12345678L,
                        "NU",
                        LocalDate.now(),
                        usuario0,
                        new BigDecimal(15000),
                        new BigDecimal(15000));

                ContaModel conta0 = new ContaModel(
                        null,
                        321654987L,
                        "BRADESCO",
                        usuario0,
                        new BigDecimal(2000));


                TipoLancamentoModel tipoReceita = new TipoLancamentoModel(null, "RECEITA");
                TipoLancamentoModel tipoDespesa = new TipoLancamentoModel(null, "DESPESA");
                FonteLancamentoModel fonteSaldo = new FonteLancamentoModel(null, "SALDO");
                FonteLancamentoModel fonteCredito = new FonteLancamentoModel(null, "CREDITO");

                ContaLancamentoModel lancamentoConta = new ContaLancamentoModel(
                        null,
                        new BigDecimal(100),
                        LocalDate.now(),
                        usuario0,
                        tipoReceita,
                        fonteSaldo,
                        conta0);

                CartaoLancamentoModel lancamentoCartao = new CartaoLancamentoModel(
                        null,
                        new BigDecimal(200),
                        LocalDate.now(),
                        usuario0,
                        tipoDespesa,
                        fonteCredito,
                        cartao0
                );


                usuario0.setCartoes(Set.of(cartao0));
                usuario0.setContas(Set.of(conta0));
                usuario0.setLancamentos(Set.of(lancamentoConta,lancamentoCartao));

                entityManager.persist(usuario0);
                entityManager.persist(cartao0);
                entityManager.persist(conta0);
                entityManager.persist(fonteCredito);
                entityManager.persist(fonteSaldo);
                entityManager.persist(tipoDespesa);
                entityManager.persist(tipoReceita);
                entityManager.persist(lancamentoConta);
                entityManager.persist(lancamentoCartao);
                entityManager.flush();
        }


        @DisplayName("Given UsuarioId When FindByIdWithCartoes Then Return Usuario With Cartoes")
        @Test
        void givenUsuarioId_whenFindByIdWithCartoes_thenReturnUsuarioWithCartoes() {
                // Given / Arrange

                // When / Act
                Optional<UsuarioModel> usuarioOptional = usuarioRepository.findByIdWithCartoes(usuario0.getId());

                // Then / Assert
                Assertions.assertTrue(usuarioOptional.isPresent());
                Assertions.assertNotNull(usuarioOptional.get().getCartoes());

                Optional<CartaoModel> cartaoRetornado = usuarioOptional.get().getCartoes().stream().filter(
                        cartaoModel -> cartaoModel.getBanco().equals("NU")
                ).findFirst();

                Assertions.assertTrue(cartaoRetornado.isPresent());

        }

        @DisplayName("Given UsuarioId When FindByIdWithContas Then Return Usuario With Contas")
        @Test
        void givenUsuarioId_whenFindByIdWithContas_thenReturnUsuarioWithContas() {
                // Given / Arrange

                // When / Act
                Optional<UsuarioModel> usuarioOptional = usuarioRepository.findByIdWithContas(usuario0.getId());

                // Then / Assert
                Assertions.assertTrue(usuarioOptional.isPresent());
                Assertions.assertNotNull(usuarioOptional.get().getContas());

                Optional<ContaModel> contaRetornada = usuarioOptional.get().getContas().stream().filter(
                        contaModel -> contaModel.getBanco().equals("BRADESCO")
                ).findFirst();

                Assertions.assertTrue(contaRetornada.isPresent());

        }

        // test[System Under Test]_[Condition or State Change]_[Expected Result]
        @DisplayName("Given UsuarioId When findByIdWithCartoesAndContas Then Return Usuario with Contas and Cartoes")
        @Test
        void given_UsuarioId_whenFindByIdWithCartoesAndContas_thenReturnUsuarioWithContasAndCartoes() {
                // Given / Arrange
                // When / Act
                Optional<UsuarioModel> usuarioOptional = usuarioRepository.findByIdWithCartoesAndContas(usuario0.getId());

                // Then / Assert
                Assertions.assertTrue(usuarioOptional.isPresent());
                Assertions.assertNotNull(usuarioOptional.get().getCartoes());
                Assertions.assertNotNull(usuarioOptional.get().getContas());

                Optional<CartaoModel> cartaoRetornado = usuarioOptional.get().getCartoes().stream().filter(
                        cartaoModel -> cartaoModel.getBanco().equals("NU")
                ).findFirst();

                Optional<ContaModel> contaRetornada = usuarioOptional.get().getContas().stream().filter(
                        contaModel -> contaModel.getBanco().equals("BRADESCO")
                ).findFirst();

                Assertions.assertTrue(cartaoRetornado.isPresent());
                Assertions.assertTrue(contaRetornada.isPresent());

        }

        // test[System Under Test]_[Condition or State Change]_[Expected Result]
        @DisplayName("Given UsuarioId When findByIdWithLancamentos Then Return Usuario With Lancamentos")
        @Test
        void given_UsuarioId_whenFindByIdWithLancamentos_thenReturnUsuarioWithLancamentos() {
                // Given / Arrange
                // When / Act
                Optional<UsuarioModel> usuarioOptional = usuarioRepository.findByIdWithLancamentos(usuario0.getId());
                // Then / Assert

                Assertions.assertTrue(usuarioOptional.isPresent());
                Assertions.assertNotNull(usuarioOptional.get().getCartoes());
                Assertions.assertNotNull(usuarioOptional.get().getContas());
                Assertions.assertNotNull(usuarioOptional.get().getLancamentos());
                Assertions.assertEquals(2, usuarioOptional.get().getLancamentos().size());

                Optional<CartaoModel> cartaoRetornado = usuarioOptional.get().getCartoes().stream().filter(
                        cartaoModel -> cartaoModel.getBanco().equals("NU")
                ).findFirst();

                Optional<ContaModel> contaRetornada = usuarioOptional.get().getContas().stream().filter(
                        contaModel -> contaModel.getBanco().equals("BRADESCO")
                ).findFirst();

                Assertions.assertTrue(cartaoRetornado.isPresent());
                Assertions.assertTrue(contaRetornada.isPresent());





        }
}
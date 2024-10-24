package com.sskings.shopping_delivery.services;

import static org.mockito.BDDMockito.*;

import com.sskings.shopping_delivery.models.ClienteModel;
import com.sskings.shopping_delivery.models.EnderecoModel;
import com.sskings.shopping_delivery.repositories.EnderecoRepository;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class EnderecoServiceTest {

    @Mock
    private EnderecoRepository enderecoRepository;

    @InjectMocks
    private EnderecoService enderecoService;

    private EnderecoModel enderecoModel;

    @BeforeEach
    void setUp(){

        enderecoModel = new EnderecoModel(
                1L,
                "Rua Test",
                "B12",
                "Bairro Test",
                "Complemento",
                LocalDateTime.now(),
                new ClienteModel(null, "usuario_test", "test@email.com",
                        "00-00000-0000", "000.000.000-00", LocalDateTime.now(),
                                List.of(), List.of())
        );

    }


    @DisplayName("Deve Salvar Um Endereço Com Sucesso")
    @Test
    void deveSalvarUmEnderecoComSucesso() {
        // Given / Arrange
        when(enderecoRepository.save(enderecoModel)).thenReturn(enderecoModel);
        // When / Act
        EnderecoModel enderecoRetornado = enderecoService.salvar(enderecoModel);
        // Then / Assert
        assertNotNull(enderecoRetornado);
        assertEquals("B12", enderecoRetornado.getNumero());
        verify(enderecoRepository, times(1)).save(enderecoModel);
    }


    @DisplayName("Deve Listar Enderecos")
    @Test
    void deveListarEnderecos() {
        // Given / Arrange
        EnderecoModel enderecoModel2 = new EnderecoModel(
                null,
                "Rua Test2",
                "B13",
                "Bairro Test2",
                "Complemento2",
                LocalDateTime.now(),
                new ClienteModel(null, "usuario_test2", "test2@email.com", "00-00000-0002", "000.000.000-02", LocalDateTime.now(),
                        List.of(), List.of())
        );
        when(enderecoRepository.findAll()).thenReturn(List.of(enderecoModel, enderecoModel2));
        // When / Act
        List<EnderecoModel> listaRetornada = enderecoService.listar();
        // Then / Assert
        assertNotNull(listaRetornada);
        assertEquals(2, listaRetornada.size());
        verify(enderecoRepository, times(1)).findAll();

    }

    @DisplayName("Deve Retornar Uma Lista Vazia Quando Não Há Endereços")
    @Test
    void deveRetornarUmaListaVaziaQuandoNaoHaEnderecos() {
        // Given / Arrange
        when(enderecoRepository.findAll()).thenReturn(Collections.emptyList());
        // When / Act
        List<EnderecoModel> listaRetornada = enderecoService.listar();
        // Then / Assert
        assertNotNull(listaRetornada);
        assertTrue(listaRetornada.isEmpty());
        verify(enderecoRepository, times(1)).findAll();

    }

    @DisplayName("Deve Buscar Um Endereço Por Id com Sucesso")
    @Test
    void deveBuscarUmEnderecoPorIdComSucesso() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.of(enderecoModel));
        // When / Act
        EnderecoModel enderecoRetornado = enderecoService.buscarPorId(1L);
        // Then / Assert
        assertNotNull(enderecoRetornado);
        assertSame(1L, enderecoRetornado.getId());
        assertEquals("B12", enderecoRetornado.getNumero());
        verify(enderecoRepository, times(1)).findById(1L);
    }

    @DisplayName("Deve Lançar Exceção Quando Endereço Não Encontrado Por Id")
    @Test
    void deveLancarExcecaoQuandoEnderecoNaoEncontradoPorId() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> enderecoService.buscarPorId(1L));
        // Then / Assert
        assertEquals("Endereço não encontrado.", exception.getMessage());
        verify(enderecoRepository, times(1)).findById(1L);
    }

    // test[System Under Test]_[Condition or State Change]_[Expected Result]
    @DisplayName("Deve Atualizar Endereço com Sucesso")
    @Test
    void deveAtualizarEnderecoComSucesso() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.of(enderecoModel));
        when(enderecoRepository.save(enderecoModel)).thenReturn(enderecoModel);
        // When / Act
        enderecoModel.setNumero("A10");
        EnderecoModel enderecoRetornado = enderecoService.atualizar(1L ,enderecoModel);
        // Then / Assert

        assertNotNull(enderecoRetornado);
        assertEquals("A10", enderecoRetornado.getNumero());
        verify(enderecoRepository, times(1)).save(enderecoModel);
    }

    @DisplayName("Deve Lançar Exceção Quando Atualizar Endereço Não Encontrado.")
    @Test
    void deveLancarExcecaoQuandoAtualizarEnderecoNaoEncontrado() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        enderecoModel.setNumero("A10");
        Exception exception = assertThrows(RuntimeException.class, () -> enderecoService.atualizar(1L, enderecoModel));
        // Then / Assert
        assertEquals("Endereço não encontrado.", exception.getMessage());
        verify(enderecoRepository, times(1)).findById(1L);
        verify(enderecoRepository, never()).save(any(EnderecoModel.class));
    }

    @DisplayName("Deve Remover Um Endereço Por ID Com Sucesso")
    @Test
    void deveRemoverUmEnderecoPorIdComSucesso() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.of(enderecoModel));
        willDoNothing().given(enderecoRepository).deleteById(1L);
        // When / Act
        enderecoService.removerPorId(1L);
        // Then / Assert
        verify(enderecoRepository, times(1)).findById(1L);
        verify(enderecoRepository, times(1)).deleteById(1L);
    }

    @DisplayName("Deve Lançar Uma Exceção Quando RemoverPorId Endereço Não Encontrado.")
    @Test
    void deveLancarUmaExcecaoQuandoRemoverPorIdEnderecoNaoEncontrado() {
        // Given / Arrange
        when(enderecoRepository.findById(1L)).thenReturn(Optional.empty());
        // When / Act
        Exception exception = assertThrows(RuntimeException.class, () -> enderecoService.removerPorId(1L));
        // Then / Assert
        assertEquals("Endereço não encontrado.", exception.getMessage());
        verify(enderecoRepository, times(1)).findById(1L);
        verify(enderecoRepository, never()).deleteById(1L);
    }
}
package com.sskings.api.gestor.financeiro.services;

import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioRequestDto;
import com.sskings.api.gestor.financeiro.dto.usuario.UsuarioSimpleResponseDto;
import com.sskings.api.gestor.financeiro.exception.ConflictException;
import com.sskings.api.gestor.financeiro.exception.NotFoundException;
import com.sskings.api.gestor.financeiro.models.UsuarioModel;
import com.sskings.api.gestor.financeiro.models.UsuarioRole;
import com.sskings.api.gestor.financeiro.repositories.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.BDDMockito.*;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PasswordEncoder encoder;

    @InjectMocks
    private UsuarioService usuarioService;

    private UsuarioModel usuario0;


    @BeforeEach
    public void setup(){
        usuario0 = new UsuarioModel(
                null,
                "sergio",
                encoder.encode("123"),
                UsuarioRole.ADMIN,
                "sergio@email.com",
                LocalDate.now(),
                null,
                null,
                null);
    }


    @DisplayName("Given UsuarioRequestDto When Save Then Return Usuario Object")
    @Test
    void testGivenUsuarioRequestDtoObject_whenSave_thenReturnUsuarioObject() {
        // Given / Arrange
        given(usuarioRepository.existsByEmail(anyString())).willReturn(false);
        given(usuarioRepository.save(usuario0)).willReturn(usuario0);

        UsuarioRequestDto usuarioDto = new UsuarioRequestDto(
                usuario0.getUsername(),
                usuario0.getPassword(),
                usuario0.getRole(),
                usuario0.getEmail()
        );

        // When / Act

        UsuarioModel usuarioSaved = usuarioService.save(usuarioDto);

        // Then / Assert
        verify(encoder).encode(usuarioDto.password());
        assertNotNull(usuarioSaved);
        assertEquals("sergio@email.com",usuarioSaved.getEmail());

    }


    @DisplayName("Given Existing Email When Save Usuario Then Throws Exception")
    @Test
    void testGivenExistingEmail_whenSaveUsuario_thenThrowsException() {
        // Given / Arrange
        given(usuarioRepository.existsByEmail(anyString())).willReturn(true);
        UsuarioRequestDto usuarioDto = new UsuarioRequestDto(
                usuario0.getUsername(),
                usuario0.getPassword(),
                usuario0.getRole(),
                usuario0.getEmail()
        );
        // When / Act
        assertThrows(ConflictException.class, () -> {
            usuarioService.save(usuarioDto);
        });
        // Then / Assert
        verify(usuarioRepository, never()).save(any(UsuarioModel.class));
    }

    @DisplayName("Given Usuarios List When FindAll Usuarios  Then Return UsuarioSimpleResponseDto List")
    @Test
    void testGivenUsuariosList_whenFindAllUsuarios_whenReturnUsuarioSimpleResponseDtoList() {
        // Given / Arrange
        UsuarioModel usuario1 = new UsuarioModel(
                null,
                "vera",
                encoder.encode("123"),
                UsuarioRole.ADMIN,
                "vera@email.com",
                LocalDate.now(),
                null,
                null,
                null);
        given(usuarioRepository.findAll()).willReturn(List.of(usuario0, usuario1));
        // When / Act
        List<UsuarioSimpleResponseDto> usuariosList = usuarioService.findAll();
        // Then / Assert
        assertNotNull(usuariosList);
        assertEquals(2, usuariosList.size());
    }


    @DisplayName("Given No Usuarios When FindAll Usuarios Then Throws Exception")
    @Test
    void testFindAll_whenUsuarioNotFound_thenThrowsException() {
        // Given / Arrange
        given(usuarioRepository.findAll()).willReturn(List.of());
        // When / Act
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            usuarioService.findAll();
        });
        // Then / Assert
        assertEquals("não há usuários cadastrados.", exception.getMessage());

    }


    @DisplayName("Given UsuarioId When FindById Then Return UsuarioSimpleResponseDto")
    @Test
    void testGivenUsuarioId_whenFindById_thenReturnUsuarioSimpleResponseDto() {
        // Given / Arrange
        UUID id = UUID.randomUUID();
        given(usuarioRepository.findById(id)).willReturn(Optional.of(usuario0));
        // When / Act
        UsuarioSimpleResponseDto usuarioRetorned = usuarioService.findById(id);
        // Then / Assert
        assertNotNull(usuarioRetorned);
        assertEquals("sergio", usuarioRetorned.username());
        assertEquals("sergio@email.com", usuarioRetorned.email());
    }


    @DisplayName("Given Incorrect UsuarioId When FindById When Throws Exception")
    @Test
    void testGivenIncorrectId_whenFindById_thenThrowsException() {
        // Given / Arrange
        UUID id = UUID.randomUUID();
        given(usuarioRepository.findById(id)).willReturn(Optional.empty());
        // When / Act
        NotFoundException exception = assertThrows(NotFoundException.class, () -> {
            usuarioService.findById(id);
        });
        // Then / Assert
        assertEquals("Usuário não encontrado.", exception.getMessage());
    }

}
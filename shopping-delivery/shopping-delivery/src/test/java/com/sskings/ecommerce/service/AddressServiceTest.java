package com.sskings.ecommerce.service;

import com.sskings.ecommerce.dto.request.AddressRequest;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.Address;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.AddressRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AddressServiceTest {

    @Mock AddressRepository addressRepository;
    @Mock UserRepository userRepository;
    @InjectMocks AddressService service;

    private final AddressRequest request =
            new AddressRequest("01000-000", "Rua A", "10", null, "Centro", "São Paulo", "SP");

    @Test
    @DisplayName("Cria endereço vinculado ao usuário")
    void create_success() {
        User user = User.builder().id(1L).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(addressRepository.save(any())).willAnswer(inv -> {
            Address a = inv.getArgument(0);
            a.setId(5L);
            return a;
        });

        var resp = service.create(request, 1L);

        assertThat(resp.id()).isEqualTo(5L);
        assertThat(resp.city()).isEqualTo("São Paulo");
    }

    @Test
    @DisplayName("Usuário inexistente gera 404")
    void create_userNotFound_throws() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.create(request, 9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Lista apenas endereços do usuário")
    void findAllByUser() {
        given(addressRepository.findAllByUserId(1L)).willReturn(List.of(
                Address.builder().id(1L).street("Rua A").build(),
                Address.builder().id(2L).street("Rua B").build()));

        assertThat(service.findAllByUser(1L)).extracting("street").containsExactly("Rua A", "Rua B");
    }

    @Test
    @DisplayName("Exclui endereço do próprio usuário")
    void delete_success() {
        Address address = Address.builder().id(3L).build();
        given(addressRepository.findByIdAndUserId(3L, 1L)).willReturn(Optional.of(address));

        service.delete(3L, 1L);

        verify(addressRepository).delete(address);
    }

    @Test
    @DisplayName("Não exclui endereço de outro usuário")
    void delete_otherUsersAddress_throws() {
        given(addressRepository.findByIdAndUserId(3L, 2L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(3L, 2L)).isInstanceOf(ResourceNotFoundException.class);
        verify(addressRepository, never()).delete(any());
    }
}

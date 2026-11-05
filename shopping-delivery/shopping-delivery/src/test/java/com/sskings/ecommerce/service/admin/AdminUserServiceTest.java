package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import com.sskings.ecommerce.exception.ResourceNotFoundException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @InjectMocks AdminUserService service;

    @Test
    @DisplayName("Admin bloqueia um cliente")
    void update_blocksCustomer() {
        User customer = User.builder().id(2L).name("Ana").email("ana@a.com").build();
        given(userRepository.findById(2L)).willReturn(Optional.of(customer));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(orderRepository.countByUserId(2L)).willReturn(4L);

        var resp = service.update(2L, new UpdateUserRequest(false, null), 1L);

        assertThat(resp.active()).isFalse();
        assertThat(resp.role()).isEqualTo(Role.CUSTOMER);
        assertThat(resp.orderCount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("Admin não pode bloquear/rebaixar a si mesmo")
    void update_self_throws() {
        User admin = User.builder().id(1L).role(Role.ADMIN).build();
        given(userRepository.findById(1L)).willReturn(Optional.of(admin));

        assertThatThrownBy(() -> service.update(1L, new UpdateUserRequest(false, Role.CUSTOMER), 1L))
                .isInstanceOf(BusinessException.class);
        assertThat(admin.isActive()).isTrue();
    }

    @Test
    @DisplayName("Promove cliente a admin")
    void update_promotesToAdmin() {
        User customer = User.builder().id(2L).build();
        given(userRepository.findById(2L)).willReturn(Optional.of(customer));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.update(2L, new UpdateUserRequest(null, Role.ADMIN), 1L);

        assertThat(resp.role()).isEqualTo(Role.ADMIN);
        assertThat(resp.active()).isTrue();
    }

    @Test
    @DisplayName("Lista com e sem busca")
    void findAll_withAndWithoutSearch() {
        var pageable = PageRequest.of(0, 10);
        User u = User.builder().id(2L).name("Ana").email("ana@a.com").build();
        given(userRepository.findAll(pageable)).willReturn(new PageImpl<>(List.of(u)));
        given(userRepository.findAllByNameContainingIgnoreCaseOrEmailContainingIgnoreCase("ana", "ana", pageable))
                .willReturn(new PageImpl<>(List.of(u)));

        assertThat(service.findAll(null, pageable).getTotalElements()).isEqualTo(1);
        assertThat(service.findAll("ana", pageable).getContent()).extracting("email").containsExactly("ana@a.com");
    }

    @Test
    @DisplayName("Pedidos de usuário inexistente geram 404")
    void findOrders_userNotFound() {
        given(userRepository.findById(9L)).willReturn(Optional.empty());
        assertThatThrownBy(() -> service.findOrders(9L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("Pedidos do usuário")
    void findOrders_success() {
        given(userRepository.findById(2L)).willReturn(Optional.of(User.builder().id(2L).build()));
        given(orderRepository.findAllByUserIdWithItems(2L)).willReturn(List.of());

        assertThat(service.findOrders(2L)).isEmpty();
    }
}

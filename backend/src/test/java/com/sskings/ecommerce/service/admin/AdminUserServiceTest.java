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
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {

    @Mock UserRepository userRepository;
    @Mock OrderRepository orderRepository;
    @Mock com.sskings.ecommerce.service.AuditService auditService;
    @Mock com.sskings.ecommerce.service.AccountService accountService;
    @Mock org.springframework.context.ApplicationEventPublisher events;
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

    // ---------- revisão 4, R4-3: mudança sensível exige a senha (e o MFA) de quem age ----------

    private final User me = User.builder().id(1L).email("chefe@a.com").role(Role.ADMIN).build();

    @Test
    @DisplayName("R4-3: promover a admin exige reautenticação de quem age; avisa os admins")
    void update_promotesToAdmin() {
        User customer = User.builder().id(2L).email("ana@a.com").build();
        given(userRepository.findById(2L)).willReturn(Optional.of(customer));
        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        var resp = service.update(2L, new UpdateUserRequest(null, Role.ADMIN, "minhaSenha1", "123456"), 1L);

        assertThat(resp.role()).isEqualTo(Role.ADMIN);
        assertThat(customer.getTokenVersion()).isEqualTo(1);
        then(accountService).should().reauthenticate(me,
                new com.sskings.ecommerce.dto.request.PasswordConfirmationRequest("minhaSenha1", "123456"));
        then(events).should().publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(e ->
                e instanceof com.sskings.ecommerce.event.SecurityAlertEvent a
                        && a.text().contains("#2 (a***@a.com) agora é ADMIN") && a.text().contains("admin #1")));
    }

    @Test
    @DisplayName("R4-3: sem senha, promover/rebaixar ou bloquear um admin é recusado antes de mudar qualquer coisa")
    void update_sensitiveWithoutPassword() {
        User customer = User.builder().id(2L).build();
        User otherAdmin = User.builder().id(3L).role(Role.ADMIN).build();
        given(userRepository.findById(2L)).willReturn(Optional.of(customer));
        given(userRepository.findById(3L)).willReturn(Optional.of(otherAdmin));

        assertThatThrownBy(() -> service.update(2L, new UpdateUserRequest(null, Role.ADMIN), 1L))
                .hasMessage("Password confirmation required");
        assertThatThrownBy(() -> service.update(3L, new UpdateUserRequest(false, null, " ", null), 1L))
                .hasMessage("Password confirmation required");
        assertThat(customer.getRole()).isEqualTo(Role.CUSTOMER);
        assertThat(otherAdmin.isActive()).isTrue();
        then(accountService).shouldHaveNoInteractions();
        then(events).shouldHaveNoInteractions();
    }

    @Test
    @DisplayName("R4-3: senha errada (reautenticação recusa) não muda nada")
    void update_wrongPassword() {
        User otherAdmin = User.builder().id(3L).role(Role.ADMIN).email("b@a.com").build();
        given(userRepository.findById(3L)).willReturn(Optional.of(otherAdmin));
        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        willThrow(new BusinessException("Invalid password")).given(accountService).reauthenticate(eq(me), any());

        assertThatThrownBy(() -> service.update(3L, new UpdateUserRequest(null, Role.CUSTOMER, "errada", null), 1L))
                .hasMessage("Invalid password");
        assertThat(otherAdmin.getRole()).isEqualTo(Role.ADMIN);
        then(userRepository).should(never()).save(any());
    }

    @Test
    @DisplayName("R4-3: bloquear e desbloquear admin (com senha) e rebaixar avisam os admins; bloquear cliente não pede senha")
    void update_adminTargetsAlert() {
        User otherAdmin = User.builder().id(3L).role(Role.ADMIN).email("b@a.com").build();
        given(userRepository.findById(3L)).willReturn(Optional.of(otherAdmin));
        given(userRepository.findById(1L)).willReturn(Optional.of(me));
        given(userRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.update(3L, new UpdateUserRequest(false, null, "minhaSenha1", null), 1L);
        service.update(3L, new UpdateUserRequest(true, null, "minhaSenha1", null), 1L);
        service.update(3L, new UpdateUserRequest(null, Role.CUSTOMER, "minhaSenha1", null), 1L);

        var alerts = org.mockito.ArgumentCaptor.forClass(Object.class);
        then(events).should(times(3)).publishEvent(alerts.capture());
        assertThat(alerts.getAllValues()).extracting(e -> ((com.sskings.ecommerce.event.SecurityAlertEvent) e).text())
                .anySatisfy(t -> assertThat(t).contains("foi bloqueado"))
                .anySatisfy(t -> assertThat(t).contains("foi desbloqueado"))
                .anySatisfy(t -> assertThat(t).contains("deixou de ser admin"));
        then(accountService).should(times(3)).reauthenticate(eq(me), any());
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

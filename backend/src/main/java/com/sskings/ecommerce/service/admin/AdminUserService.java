package com.sskings.ecommerce.service.admin;

import com.sskings.ecommerce.dto.request.UpdateUserRequest;
import com.sskings.ecommerce.dto.response.OrderResponse;
import com.sskings.ecommerce.dto.response.UserResponse;
import com.sskings.ecommerce.exception.BusinessException;
import com.sskings.ecommerce.exception.ResourceNotFoundException;
import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.repository.OrderRepository;
import com.sskings.ecommerce.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final com.sskings.ecommerce.service.AuditService auditService;
    private final com.sskings.ecommerce.service.AccountService accountService;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public Page<UserResponse> findAll(String search, Pageable pageable) {
        Page<User> page = (search != null && !search.isBlank())
                ? userRepository.findAllByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(search, search, pageable)
                : userRepository.findAll(pageable);
        return page.map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public UserResponse findById(Long id) {
        return toResponse(getUser(id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findOrders(Long userId) {
        getUser(userId);
        return orderRepository.findAllByUserIdWithItems(userId).stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse update(Long id, UpdateUserRequest request, Long currentAdminId) {
        User user = getUser(id);
        if (Objects.equals(id, currentAdminId)) {
            throw new BusinessException("You cannot change your own account");
        }
        boolean roleChange = request.role() != null && request.role() != user.getRole();
        boolean activeChange = request.active() != null && request.active() != user.isActive();
        boolean targetIsAdmin = user.getRole() == com.sskings.ecommerce.model.enums.Role.ADMIN;
        // Mudança sensível = criar/remover admin ou mexer numa conta de admin: exige a senha (e o MFA) de quem age.
        // Bloquear/desbloquear cliente continua direto (rotina de atendimento, também pelo bot). Revisão 4, R4-3.
        if (roleChange || (activeChange && targetIsAdmin)) {
            if (request.password() == null || request.password().isBlank()) {
                throw new BusinessException("Password confirmation required");
            }
            User admin = getUser(currentAdminId);
            accountService.reauthenticate(admin, new com.sskings.ecommerce.dto.request.PasswordConfirmationRequest(
                    request.password(), request.mfaCode()));
        }
        StringBuilder changes = new StringBuilder();
        if (request.active() != null && request.active() != user.isActive()) {
            user.setActive(request.active());
            changes.append("active=").append(request.active()).append(' ');
        }
        if (request.role() != null && request.role() != user.getRole()) {
            changes.append("role ").append(user.getRole()).append("->").append(request.role());
            user.setRole(request.role());
        }
        if (!changes.isEmpty()) {
            user.revokeTokens();   // bloqueio ou troca de papel derrubam as sessões abertas
            auditService.record(null, com.sskings.ecommerce.service.AuditService.USER_UPDATED, "USER", id, changes.toString().trim());
            if (roleChange || targetIsAdmin) {
                String who = "#" + id + " (" + com.sskings.ecommerce.service.AuditService.maskEmail(user.getEmail()) + ")";
                String what = roleChange && request.role() == com.sskings.ecommerce.model.enums.Role.ADMIN
                        ? "Usuário " + who + " agora é ADMIN."
                        : roleChange ? "Admin " + who + " deixou de ser admin."
                        : "Admin " + who + (user.isActive() ? " foi desbloqueado." : " foi bloqueado.");
                events.publishEvent(new com.sskings.ecommerce.event.SecurityAlertEvent(
                        what + " Feito pelo admin #" + currentAdminId + ". Se não foi você, troque a senha agora."));
            }
        }
        return toResponse(userRepository.save(user));
    }

    private User getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    private UserResponse toResponse(User user) {
        return UserResponse.from(user, orderRepository.countByUserId(user.getId()));
    }
}

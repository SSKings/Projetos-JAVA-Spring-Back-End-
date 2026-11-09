package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.User;
import com.sskings.ecommerce.model.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);

    /**
     * Trava a linha do usuário até o fim da transação (SELECT ... FOR UPDATE): serializa operações concorrentes do
     * mesmo cliente que fazem "procura, senão insere" (ver CartService.addItem).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :id")
    Optional<User> findByIdForUpdate(@Param("id") Long id);

    /**
     * Grava o passo TOTP aceito só se ele for mais novo que o último (revisão 4, R4-6/R4-7). É atômico: dois logins
     * simultâneos com o mesmo código não passam os dois (o segundo encontra a linha já atualizada → 0), e não regrava
     * a entidade inteira — gravar um User lido antes podia desfazer um bloqueio feito no mesmo instante.
     * @return 1 se o passo foi aceito; 0 se o código já foi usado.
     */
    @Transactional
    @Modifying(flushAutomatically = true)
    @Query("update User u set u.mfaLastStep = :step where u.id = :id and (u.mfaLastStep is null or u.mfaLastStep < :step)")
    int claimMfaStep(@Param("id") Long id, @Param("step") long step);

    // Admin
    Page<User> findAllByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(String name, String email, Pageable pageable);
    long countByRole(Role role);

    /** Admins que ainda podem entrar (revisão 5, R5-6: o último não pode excluir a própria conta). */
    long countByRoleAndActiveTrueAndAnonymizedAtIsNull(Role role);
}

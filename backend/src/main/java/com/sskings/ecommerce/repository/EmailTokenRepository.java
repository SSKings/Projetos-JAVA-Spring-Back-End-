package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.EmailToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface EmailTokenRepository extends JpaRepository<EmailToken, Long> {

    @Query("select t from EmailToken t join fetch t.user where t.tokenHash = :hash")
    Optional<EmailToken> findByTokenHash(@Param("hash") String hash);

    Optional<EmailToken> findFirstByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, EmailToken.Purpose purpose);

    /** Links deste tipo emitidos para a conta desde {@code since} (teto diário de envios — revisão 6, R6-1). */
    long countByUserIdAndPurposeAndCreatedAtAfter(Long userId, EmailToken.Purpose purpose, LocalDateTime since);

    /** Marca como usado só se ainda não foi: dois cliques simultâneos no mesmo link não valem os dois. */
    @Modifying(flushAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.id = :id and t.usedAt is null")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    /** Invalida os links pendentes do mesmo tipo (um link novo, ou a senha já redefinida, aposenta os antigos). */
    @Modifying(flushAutomatically = true)
    @Query("update EmailToken t set t.usedAt = :now where t.user.id = :userId and t.purpose = :purpose and t.usedAt is null")
    int invalidatePending(@Param("userId") Long userId, @Param("purpose") EmailToken.Purpose purpose,
                          @Param("now") LocalDateTime now);

    void deleteAllByUserId(Long userId);

    /** Retenção: links vencidos há mais de um dia não servem para nada. */
    @Modifying
    @Query("delete from EmailToken t where t.expiresAt < :before")
    int deleteExpiredBefore(@Param("before") LocalDateTime before);
}

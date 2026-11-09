package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.TrustedLoginSource;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TrustedLoginSourceRepository extends JpaRepository<TrustedLoginSource, Long> {
    boolean existsByUserIdAndSourceKeyAndLastSuccessAtAfter(Long userId, String sourceKey, LocalDateTime after);
    Optional<TrustedLoginSource> findByUserIdAndSourceKey(Long userId, String sourceKey);
    List<TrustedLoginSource> findAllByUserIdOrderByLastSuccessAtDesc(Long userId);
    void deleteAllByUserId(Long userId);

    /** Retenção (revisão 5, R5-5): origens sem login há mais que o prazo, inclusive de quem nunca mais voltou. */
    @org.springframework.transaction.annotation.Transactional
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from TrustedLoginSource t where t.lastSuccessAt < :before")
    int deleteOlderThan(@org.springframework.data.repository.query.Param("before") LocalDateTime before);
}

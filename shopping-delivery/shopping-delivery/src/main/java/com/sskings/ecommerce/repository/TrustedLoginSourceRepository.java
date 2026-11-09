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
}

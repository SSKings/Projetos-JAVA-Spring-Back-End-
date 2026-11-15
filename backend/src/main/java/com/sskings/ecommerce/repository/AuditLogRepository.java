package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    /** Retenção (revisão 5, R5-5): apaga eventos mais antigos que o prazo. */
    @org.springframework.transaction.annotation.Transactional
    @Modifying
    @Query("delete from AuditLog a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") java.time.LocalDateTime before);

    Page<AuditLog> findAllByAction(String action, Pageable pageable);

    java.util.List<AuditLog> findTop500ByActorIdOrderByCreatedAtDesc(Long actorId);

    /** Anonimização (LGPD): o registro continua, sem o email do titular. */
    @Modifying
    @Query("UPDATE AuditLog a SET a.actorEmail = :replacement WHERE a.actorId = :actorId")
    int replaceActorEmail(@Param("actorId") Long actorId, @Param("replacement") String replacement);
}

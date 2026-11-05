package com.sskings.ecommerce.repository;

import com.sskings.ecommerce.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    Page<AuditLog> findAllByAction(String action, Pageable pageable);

    java.util.List<AuditLog> findTop500ByActorIdOrderByCreatedAtDesc(Long actorId);

    /** Anonimização (LGPD): o registro continua, sem o email do titular. */
    @Modifying
    @Query("UPDATE AuditLog a SET a.actorEmail = :replacement WHERE a.actorId = :actorId")
    int replaceActorEmail(@Param("actorId") Long actorId, @Param("replacement") String replacement);
}

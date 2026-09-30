package com.aditi.cloudvault.repository;

import com.aditi.cloudvault.model.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {


}
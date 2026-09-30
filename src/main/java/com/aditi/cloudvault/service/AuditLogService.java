package com.aditi.cloudvault.service;

import com.aditi.cloudvault.model.AuditLog;
import com.aditi.cloudvault.repository.AuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    public void logEvent(
            Long userId,
            Long fileId,
            String event,
            String result,
            String reason) {
        AuditLog auditLog = new AuditLog();

        auditLog.setUserId(userId);
        auditLog.setFileId(fileId);
        auditLog.setEvent(event);
        auditLog.setResult(result);
        auditLog.setReason(reason);
        auditLog.setTimestamp(LocalDateTime.now());

        auditLogRepository.save(auditLog);
    }
}
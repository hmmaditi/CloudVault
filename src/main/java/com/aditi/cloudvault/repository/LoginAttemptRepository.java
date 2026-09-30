package com.aditi.cloudvault.repository;

import com.aditi.cloudvault.model.LoginAttempt;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LoginAttemptRepository
        extends JpaRepository<LoginAttempt, Long> {

    Optional<LoginAttempt> findByIdentifierAndIdentifierType(
            String identifier,
            String identifierType
    );
}
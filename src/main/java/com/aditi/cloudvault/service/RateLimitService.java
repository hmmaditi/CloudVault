package com.aditi.cloudvault.service;

import com.aditi.cloudvault.model.LoginAttempt;
import com.aditi.cloudvault.repository.LoginAttemptRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class RateLimitService {

    private final LoginAttemptRepository loginAttemptRepository;

    private static final int ACCOUNT_LIMIT = 5;
    private static final int IP_LIMIT = 100;
    private static final int REGISTER_IP_LIMIT = 5;
    private static final int UPLOAD_LIMIT = 10;
    private static final int DOWNLOAD_LIMIT = 20;

    private static final int WINDOW_MINUTES = 10;
    private static final int BLOCK_MINUTES = 10;


    public RateLimitService(
            LoginAttemptRepository loginAttemptRepository) {

        this.loginAttemptRepository = loginAttemptRepository;
    }

    public boolean isBlocked(
            String identifier,
            String identifierType) {

        Optional<LoginAttempt> optionalAttempt =
                loginAttemptRepository
                        .findByIdentifierAndIdentifierType(
                                identifier,
                                identifierType
                        ); //DB se record maang rahe hain.

        if (optionalAttempt.isEmpty()) {
            return false;
        } //Agar is account/IP ka koi record hi nahi hai:

//        "Isko kabhi block nahi kiya → allow."

        LoginAttempt attempt = optionalAttempt.get();
//Optional ke andar jo actual LoginAttempt object hai, woh nikal liya.
        if (attempt.getBlockedUntil() == null) {
            return false;
        } //Record hai, but blocked nahi hai.

        return LocalDateTime.now()
                .isBefore(attempt.getBlockedUntil()); //2:05 is before 2:10 → true Therefore: true = BLOCKED
    }

    public void recordFailedAttempt(
            String identifier,
            String identifierType) {

        LocalDateTime now = LocalDateTime.now();

        Optional<LoginAttempt> optionalAttempt =
                loginAttemptRepository
                        .findByIdentifierAndIdentifierType(
                                identifier,
                                identifierType
                        );

        LoginAttempt attempt;

        if (optionalAttempt.isEmpty()) {

            attempt = new LoginAttempt();

            attempt.setIdentifier(identifier);
            attempt.setIdentifierType(identifierType);
            attempt.setFailedAttempts(1);
            attempt.setWindowStart(now);

        } else {

            attempt = optionalAttempt.get();

            LocalDateTime windowEnd =
                    attempt.getWindowStart()
                            .plusMinutes(WINDOW_MINUTES);

            if (now.isAfter(windowEnd)) {

                attempt.setFailedAttempts(1);
                attempt.setWindowStart(now);
                attempt.setBlockedUntil(null);

            } else {

                attempt.setFailedAttempts(
                        attempt.getFailedAttempts() + 1
                );
            }
        }

        loginAttemptRepository.save(attempt);
    }

    public boolean checkAndBlock(
            String identifier,
            String identifierType) {

        Optional<LoginAttempt> optionalAttempt =
                loginAttemptRepository
                        .findByIdentifierAndIdentifierType(
                                identifier,
                                identifierType
                        );

        if (optionalAttempt.isEmpty()) {
            return false;
        }

        LoginAttempt attempt = optionalAttempt.get();

        int limit;

        if (identifierType.equals("ACCOUNT")) {
            limit = ACCOUNT_LIMIT;
        } else if (identifierType.equals("IP")) {

            limit = IP_LIMIT;

        } else if (identifierType.equals("REGISTER_IP")) {

            limit = REGISTER_IP_LIMIT;

        } else if (identifierType.equals("UPLOAD")) {

            limit = UPLOAD_LIMIT;

        }
        else if (identifierType.equals("DOWNLOAD")) {

            limit = DOWNLOAD_LIMIT;

        } else {

            throw new IllegalArgumentException(
                    "Unknown identifier type"
            );
        }

            if (attempt.getFailedAttempts() >= limit) {

            attempt.setBlockedUntil(
                    LocalDateTime.now()
                            .plusMinutes(BLOCK_MINUTES)
            );

            loginAttemptRepository.save(attempt);

            return true;
        }

        return false;
    }
    public void resetAttempts(
            String identifier,
            String identifierType) {

        Optional<LoginAttempt> optionalAttempt =
                loginAttemptRepository
                        .findByIdentifierAndIdentifierType(
                                identifier,
                                identifierType
                        );

        if (optionalAttempt.isPresent()) {

            LoginAttempt attempt = optionalAttempt.get();

            attempt.setFailedAttempts(0);
            attempt.setWindowStart(LocalDateTime.now());
            attempt.setBlockedUntil(null);

            loginAttemptRepository.save(attempt);
        }
    }

    public void recordAttempt(
            String identifier,
            String identifierType) {

        recordFailedAttempt(identifier, identifierType);
    }

}
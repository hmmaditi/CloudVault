
package com.aditi.cloudvault.service;

import com.aditi.cloudvault.model.RevokedToken;
import com.aditi.cloudvault.repository.RevokedTokenRepository;
import com.aditi.cloudvault.service.JwtService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class JwtRevocationService {

    private final JwtService jwtService;
    private final RevokedTokenRepository revokedTokenRepository;

    public JwtRevocationService(
            JwtService jwtService,
            RevokedTokenRepository revokedTokenRepository) {

        this.jwtService = jwtService;
        this.revokedTokenRepository = revokedTokenRepository;
    }

    public void revokeToken(String token) {

        String jti = jwtService.extractJti(token);

        LocalDateTime expiresAt =
                jwtService.extractExpiration(token);

        RevokedToken revokedToken = new RevokedToken();

        revokedToken.setJti(jti);
        revokedToken.setExpiresAt(expiresAt);

        revokedTokenRepository.save(revokedToken);
    }
}


package com.aditi.cloudvault.controller;


import com.aditi.cloudvault.dto.LoginRequest;
import com.aditi.cloudvault.dto.RegisterRequest;
import com.aditi.cloudvault.security.JwtAuthenticationFilter;
import com.aditi.cloudvault.service.AuthService;
import com.aditi.cloudvault.service.JwtRevocationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpServletRequest;

    @RestController
    @RequestMapping("/auth")
    public class AuthController {

        private final AuthService authService;
        private final JwtRevocationService jwtRevocationService;

        public AuthController(AuthService authService,
        JwtRevocationService jwtRevocationService
        ) {
            this.authService = authService;
            this.jwtRevocationService = jwtRevocationService;

        }

        @PostMapping("/register")
        public ResponseEntity<String> register(
                @Valid @RequestBody RegisterRequest request,
                HttpServletRequest httpRequest) {

            authService.register(request, httpRequest);

            return ResponseEntity.ok("User registered successfully");
        }

        @PostMapping("/login")
        public ResponseEntity<String> login(

                @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
            String token = authService.login(request, httpRequest);

            return ResponseEntity.ok(token);

        }
        @PostMapping("/revoke")
        public ResponseEntity<String> revokeToken(
                @RequestHeader("Authorization") String authHeader) {

            String token = authHeader.substring(7);

            jwtRevocationService.revokeToken(token);

            return ResponseEntity.ok("JWT revoked successfully");
        }
    }


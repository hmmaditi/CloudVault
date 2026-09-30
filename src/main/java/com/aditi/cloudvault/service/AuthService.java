package com.aditi.cloudvault.service;

import com.aditi.cloudvault.dto.LoginRequest;
import com.aditi.cloudvault.dto.RegisterRequest;
import com.aditi.cloudvault.model.User;
import com.aditi.cloudvault.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.aditi.cloudvault.exception.RateLimitExceededException;

@Service

public class AuthService {
    private final PasswordEncoder passwordEncoder;
    private final UserRepository userRepository;
    public final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RateLimitService rateLimitService;
    private final HttpServletRequest httpServletRequest;


    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService,
                       RateLimitService rateLimitService,
                       HttpServletRequest httpServletRequest) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
        this.rateLimitService = rateLimitService;
        this.httpServletRequest = httpServletRequest;
    }

    public void register(RegisterRequest request,
    HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getRemoteAddr();

        if (rateLimitService.isBlocked(
                ipAddress,
                "REGISTER_IP")) {

            throw new RateLimitExceededException(
                    "Too many registration attempts. Try again later."
            );
        }

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = new User();

        user.setEmail(request.getEmail());
        user.setPasswordHash(
                passwordEncoder.encode(request.getPassword())
        );

        user.setEnabled(true);

        userRepository.save(user);

        rateLimitService.recordFailedAttempt(
                ipAddress,
                "REGISTER_IP"
        );

        rateLimitService.checkAndBlock(
                ipAddress,
                "REGISTER_IP"
        );

    }


    public String login(
            LoginRequest request,
            HttpServletRequest httpRequest) {

        String ipAddress = httpRequest.getRemoteAddr();

        if (rateLimitService.isBlocked(
                ipAddress,
                "IP")) {

            throw new RateLimitExceededException(
                    "Too many login attempts. Try again later."
            );
        }

        if (rateLimitService.isBlocked(
                request.getEmail(),
                "ACCOUNT")) {

            throw new RateLimitExceededException(
                    "Too many login attempts. Try again later."
            );
        }
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (Exception e) {
            rateLimitService.recordFailedAttempt(
                    request.getEmail(),
                    "ACCOUNT"
            );
            rateLimitService.checkAndBlock(
                    request.getEmail(),
                    "ACCOUNT"
            );

            rateLimitService.recordFailedAttempt(
                    ipAddress,
                    "IP"
            );
            rateLimitService.checkAndBlock(
                    ipAddress,
                    "IP"
            );

            throw e;
        }
            rateLimitService.resetAttempts(
                    request.getEmail(),
                    "ACCOUNT"
            );

            return jwtService.generateToken(request.getEmail());
        }


        public User getCurrentUser () {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String email = authentication.getName();
            return userRepository.findByEmail(email)
                    .orElseThrow(() ->
                            new UsernameNotFoundException("User not found"));
        }
    }




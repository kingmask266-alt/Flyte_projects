package com.flyte.controller;

import com.flyte.dto.AuthRequest;
import com.flyte.dto.AuthResponse;
import com.flyte.dto.RegisterRequest;
import com.flyte.entity.User;
import com.flyte.security.JwtUtil;
import com.flyte.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Handles user registration and login.
 * These endpoints are PUBLIC — no JWT required.
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserService userService;
    private final JwtUtil jwtUtil;
    private final AuthenticationManager authenticationManager;

    /**
     * POST /api/auth/register
     * Register a new CUSTOMER account.
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest request) {
        User user = userService.registerUser(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("User registered successfully: " + user.getUsername());
    }
    @GetMapping("/")
    public String home() {
        return "index";
    }
    /**
     * POST /api/auth/login
     * Authenticate and receive a JWT token.
     *
     * Example request body:
     * { "username": "john", "password": "password123" }
     *
     * Example response:
     * { "token": "eyJ...", "username": "john", "role": "ROLE_CUSTOMER" }
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );

        UserDetails userDetails = userService.loadUserByUsername(request.getUsername());
        String token = jwtUtil.generateToken(userDetails);
        String role = userDetails.getAuthorities().iterator().next().getAuthority();

        return ResponseEntity.ok(new AuthResponse(token, userDetails.getUsername(), role));
    }
}

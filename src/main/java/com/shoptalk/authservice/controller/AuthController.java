package com.shoptalk.authservice.controller;

import com.shoptalk.authservice.dto.AuthResponse;
import com.shoptalk.authservice.dto.LoginRequest;
import com.shoptalk.authservice.dto.RefreshTokenRequest;
import com.shoptalk.authservice.dto.RegisterRequest;
import com.shoptalk.authservice.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService){
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request){
        AuthResponse savedUser = authService.register(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(savedUser);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request){
        AuthResponse savedUser = authService.login(request);

        return ResponseEntity.status(HttpStatus.OK).body(savedUser);
    }
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request){
        AuthResponse savedUser = authService.refresh(request);

        return ResponseEntity.status(HttpStatus.OK).body(savedUser);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String authHeader) {
        String token = authHeader.substring(7);
        authService.logout(token);
        return ResponseEntity.noContent().build();
    }

}

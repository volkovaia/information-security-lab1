package org.example.infseclab1.controller;


import org.example.infseclab1.dto.LoginRequest;
import org.example.infseclab1.repository.UserRepository;
import org.example.infseclab1.security.JwtUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        var userOptional = userRepository.findByUsername(request.username());

        // Безопасная сверка хэшей через BCrypt
        if (userOptional.isPresent() && passwordEncoder.matches(request.password(), userOptional.get().getPassword())) {
            String token = jwtUtil.generateToken(request.username());
            return ResponseEntity.ok(Map.of("token", token));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
    }
}
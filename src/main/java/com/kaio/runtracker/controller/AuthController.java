package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.LoginRequestDTO;
import com.kaio.runtracker.dto.LoginResponseDTO;
import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.repository.UserRepository;
import com.kaio.runtracker.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173")

public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    public AuthController(
            UserRepository userRepository,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto) {

        User user = userRepository.findByEmail(dto.getEmail())
                .orElse(null);

        if (user == null || !user.getSenha().equals(dto.getSenha())) {
            return ResponseEntity.status(401).build();
        }

        String token = jwtService.gerarToken(user);

        LoginResponseDTO response = new LoginResponseDTO(
                token,
                user.getId(),
                user.getNome(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }
}

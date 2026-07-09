package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.LoginRequestDTO;
import com.kaio.runtracker.dto.LoginResponseDTO;
import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.repository.UserRepository;
import com.kaio.runtracker.security.JwtService;
import jakarta.validation.Valid;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "http://localhost:5173")

public class AuthController {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    public AuthController(
            UserRepository userRepository,
            JwtService jwtService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtService = jwtService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(
            @Valid @RequestBody LoginRequestDTO dto) {

        System.out.println("ENTROU NO LOGIN");

        User user = userRepository.findByEmail(dto.getEmail())
                .orElse(null);

        System.out.println("Email recebido: " + dto.getEmail());
        System.out.println("User encontrado: " + (user != null));

        if (user != null) {
        System.out.println("Senha digitada: " + dto.getSenha());
        System.out.println("Senha banco: " + user.getSenha());
        System.out.println("Matches: " + passwordEncoder.matches(dto.getSenha(), user.getSenha()));
}

        if (user == null || !passwordEncoder.matches(dto.getSenha(), user.getSenha())) {
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

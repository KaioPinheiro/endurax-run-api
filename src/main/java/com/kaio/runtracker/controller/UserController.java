package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.WorkoutResponseDTO;
import com.kaio.runtracker.dto.UserRequestDTO;
import com.kaio.runtracker.dto.UserResponseDTO;
import com.kaio.runtracker.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")

public class UserController {
    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> listarUsuarios() {
        return ResponseEntity.ok(userService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> buscarPorId(@PathVariable Long id) {

        UserResponseDTO user = userService.buscarPorId(id);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(user);
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> salvarUsuario(
            @Valid @RequestBody UserRequestDTO dto) {

        UserResponseDTO userSalvo = userService.salvar(dto);

        return ResponseEntity.status(201).body(userSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> atualizarUsuario(
            @PathVariable Long id,
            @Valid @RequestBody UserRequestDTO dto) {

        UserResponseDTO userAtualizado = userService.atualizar(id, dto);

        if (userAtualizado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(userAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarUsuario(@PathVariable Long id) {

        UserResponseDTO user = userService.buscarPorId(id);

        if (user == null) {
            return ResponseEntity.notFound().build();
        }

        userService.deletar(id);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/workouts")
    public ResponseEntity<List<WorkoutResponseDTO>> listarWorkoutsDoUsuario(
            @PathVariable Long id) {

        return ResponseEntity.ok(userService.listarWorkoutsDoUsuario(id));
    }
}

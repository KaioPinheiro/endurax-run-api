package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.TreinoRequestDTO;
import com.kaio.runtracker.dto.TreinoResponseDTO;
import com.kaio.runtracker.service.TreinoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;

@RestController
@RequestMapping("/treinos")
@CrossOrigin(origins = "http://localhost:5173")
public class TreinoController {

    private final TreinoService treinoService;

    public TreinoController(TreinoService treinoService) {
        this.treinoService = treinoService;
    }

    @GetMapping
    public ResponseEntity<List<TreinoResponseDTO>> listarTreinos() {
        return ResponseEntity.ok(treinoService.listarTodos());
    }

    @GetMapping("/{id}")
    public ResponseEntity<TreinoResponseDTO> buscarPorId(@PathVariable Long id) {

        TreinoResponseDTO treino = treinoService.buscarPorId(id);

        if (treino == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(treino);
    }

    @PostMapping
    public ResponseEntity<TreinoResponseDTO> salvarTreino(@Valid @RequestBody TreinoRequestDTO dto) {

        TreinoResponseDTO treinoSalvo = treinoService.salvar(dto);

        return ResponseEntity.status(201).body(treinoSalvo);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TreinoResponseDTO> atualizarTreino(
            @PathVariable Long id,
            @Valid @RequestBody TreinoRequestDTO dto
    ) {

        TreinoResponseDTO treinoAtualizado = treinoService.atualizar(id, dto);

        if (treinoAtualizado == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(treinoAtualizado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletarTreino(@PathVariable Long id) {

        TreinoResponseDTO treino = treinoService.buscarPorId(id);

        if (treino == null) {
            return ResponseEntity.notFound().build();
        }

        treinoService.deletar(id);

        return ResponseEntity.noContent().build();
    }
}

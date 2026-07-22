package com.kaio.runtracker.controller;

import com.kaio.runtracker.dto.CriarPagamentoPixRequestDTO;
import com.kaio.runtracker.dto.CriarPagamentoPixResponseDTO;
import com.kaio.runtracker.dto.PagamentoStatusResponseDTO;
import com.kaio.runtracker.dto.PagamentoResultadoResponseDTO;
import com.kaio.runtracker.service.GeracaoPlanoAssincronaService;
import com.kaio.runtracker.service.PagamentoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/pagamentos")
public class PagamentoController {
    private final PagamentoService service;
    private final GeracaoPlanoAssincronaService geracaoAssincronaService;

    public PagamentoController(
            PagamentoService service,
            GeracaoPlanoAssincronaService geracaoAssincronaService) {
        this.service = service;
        this.geracaoAssincronaService = geracaoAssincronaService;
    }

    @PostMapping("/pix")
    public ResponseEntity<CriarPagamentoPixResponseDTO> criarPix(
            @Valid @RequestBody CriarPagamentoPixRequestDTO request) {
        CriarPagamentoPixResponseDTO response = service.criarPix(
                request.email(), request.solicitacaoPlanoId());
        return ResponseEntity.created(URI.create("/api/pagamentos/" + response.pagamentoId()))
                .body(response);
    }

    @GetMapping("/{id}/status")
    public PagamentoStatusResponseDTO consultarStatus(@PathVariable Long id) {
        return service.consultarStatus(id);
    }

    @GetMapping("/{id}/resultado")
    public PagamentoResultadoResponseDTO consultarResultado(@PathVariable Long id) {
        return service.consultarResultado(id);
    }

    @PostMapping("/{id}/geracao/tentar-novamente")
    public ResponseEntity<Void> tentarNovamente(@PathVariable Long id) {
        PagamentoResultadoResponseDTO resultado = service.consultarResultado(id);
        if (resultado.pagamentoStatus() != com.kaio.runtracker.entity.PagamentoStatus.APPROVED) {
            throw new com.kaio.runtracker.exception.PagamentoException(
                    org.springframework.http.HttpStatus.CONFLICT,
                    "O pagamento ainda não foi aprovado.");
        }
        geracaoAssincronaService.iniciar(id);
        return ResponseEntity.accepted().build();
    }
}

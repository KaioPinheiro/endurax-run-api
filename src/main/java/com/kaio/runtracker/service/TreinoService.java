package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.TreinoRequestDTO;
import com.kaio.runtracker.dto.TreinoResponseDTO;
import com.kaio.runtracker.entity.Treino;
import com.kaio.runtracker.entity.User;
import com.kaio.runtracker.repository.TreinoRepository;
import com.kaio.runtracker.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class TreinoService {

    private final TreinoRepository treinoRepository;
    private final UserRepository userRepository;

    public TreinoService(TreinoRepository treinoRepository, UserRepository userRepository) {
        this.treinoRepository = treinoRepository;
        this.userRepository = userRepository;
    }

    public List<TreinoResponseDTO> listarTodos() {
        User usuarioLogado = getUsuarioLogado();

        return treinoRepository.findByUserEmail(usuarioLogado.getEmail())
                .stream()
                .map(this::converterParaResponseDTO)
                .toList();
    }

    public TreinoResponseDTO buscarPorId(Long id) {
        User usuarioLogado = getUsuarioLogado();

        Treino treino = treinoRepository.findByIdAndUserEmail(id, usuarioLogado.getEmail()).orElse(null);

        if (treino == null) {
            return null;
        }

        return converterParaResponseDTO(treino);
    }

    public TreinoResponseDTO salvar(TreinoRequestDTO dto) {

        User usuarioLogado = getUsuarioLogado();
        Treino treino = new Treino();

        treino.setDataTreino(dto.getDataTreino());
        treino.setTipo(dto.getTipo());
        treino.setDistanciaKm(dto.getDistanciaKm());
        treino.setTempoMinutos(dto.getTempoMinutos());
        treino.setPaceMedio(dto.getPaceMedio());
        treino.setObservacoes(dto.getObservacoes());
        treino.setUser(usuarioLogado);

        Treino treinoSalvo = treinoRepository.save(treino);

        return converterParaResponseDTO(treinoSalvo);
    }

    public TreinoResponseDTO atualizar(Long id, TreinoRequestDTO dto) {

        User usuarioLogado = getUsuarioLogado();
        Treino treino = treinoRepository.findByIdAndUserEmail(id, usuarioLogado.getEmail()).orElse(null);

        if (treino == null) {
            return null;
        }

        treino.setDataTreino(dto.getDataTreino());
        treino.setTipo(dto.getTipo());
        treino.setDistanciaKm(dto.getDistanciaKm());
        treino.setTempoMinutos(dto.getTempoMinutos());
        treino.setPaceMedio(dto.getPaceMedio());
        treino.setObservacoes(dto.getObservacoes());

        Treino treinoAtualizado = treinoRepository.save(treino);

        return converterParaResponseDTO(treinoAtualizado);
    }

    public void deletar(Long id) {
        User usuarioLogado = getUsuarioLogado();
        treinoRepository.findByIdAndUserEmail(id, usuarioLogado.getEmail())
                .ifPresent(treinoRepository::delete);
    }

    private User getUsuarioLogado() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getName() == null) {
            throw new ResponseStatusException(UNAUTHORIZED);
        }

        return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED));
    }

    private TreinoResponseDTO converterParaResponseDTO(Treino treino) {

        return new TreinoResponseDTO(
                treino.getId(),
                treino.getDataTreino(),
                treino.getTipo(),
                treino.getDistanciaKm(),
                treino.getTempoMinutos(),
                treino.getPaceMedio(),
                treino.getObservacoes()
        );
    }
}

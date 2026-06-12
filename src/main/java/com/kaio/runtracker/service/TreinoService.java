package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.TreinoRequestDTO;
import com.kaio.runtracker.dto.TreinoResponseDTO;
import com.kaio.runtracker.entity.Treino;
import com.kaio.runtracker.repository.TreinoRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TreinoService {

    private final TreinoRepository treinoRepository;

    public TreinoService(TreinoRepository treinoRepository) {
        this.treinoRepository = treinoRepository;
    }

    public List<TreinoResponseDTO> listarTodos() {
        return treinoRepository.findAll()
                .stream()
                .map(this::converterParaResponseDTO)
                .toList();
    }

    public TreinoResponseDTO buscarPorId(Long id) {
        Treino treino = treinoRepository.findById(id).orElse(null);

        if (treino == null) {
            return null;
        }

        return converterParaResponseDTO(treino);
    }

    public TreinoResponseDTO salvar(TreinoRequestDTO dto) {

        Treino treino = new Treino();

        treino.setDataTreino(dto.getDataTreino());
        treino.setTipo(dto.getTipo());
        treino.setDistanciaKm(dto.getDistanciaKm());
        treino.setTempoMinutos(dto.getTempoMinutos());
        treino.setPaceMedio(dto.getPaceMedio());
        treino.setObservacoes(dto.getObservacoes());

        Treino treinoSalvo = treinoRepository.save(treino);

        return converterParaResponseDTO(treinoSalvo);
    }

    public TreinoResponseDTO atualizar(Long id, TreinoRequestDTO dto) {

        Treino treino = treinoRepository.findById(id).orElse(null);

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
        treinoRepository.deleteById(id);
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
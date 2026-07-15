package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.SemanaPlanoIAResponseDTO;
import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class PlanoTreinoRespostaParser {

    private static final Logger logger =
            LoggerFactory.getLogger(PlanoTreinoRespostaParser.class);

    private static final List<String> DIAS_SEMANA = List.of(
            "segunda-feira", "terça-feira", "quarta-feira", "quinta-feira",
            "sexta-feira", "sábado", "domingo"
    );

    private final ObjectMapper objectMapper;

    public PlanoTreinoRespostaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanoTreinoIAResponseDTO parsePlanoTreino(
            String content,
            int duracaoEsperada) {
        try {
            PlanoTreinoIAResponseDTO plano =
                    objectMapper.readValue(content, PlanoTreinoIAResponseDTO.class);
            normalizarPlano(plano, duracaoEsperada);
            return plano;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            logger.error(
                    "JSON do plano completo inválido: class={}, message={}",
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "A IA retornou um plano em formato inválido. Tente novamente.",
                    exception
            );
        }
    }

    private void normalizarPlano(PlanoTreinoIAResponseDTO plano, int duracaoEsperada) {
        if (plano == null) {
            throw erroFormato("A IA retornou um plano vazio.");
        }

        int quantidadeOriginal = plano.getSemanas() == null
                ? 0
                : plano.getSemanas().size();
        logger.info(
                "Plano completo IA: quantidade de semanas retornada antes da normalização={}",
                quantidadeOriginal
        );

        Map<Integer, SemanaPlanoIAResponseDTO> semanasPorNumero = new LinkedHashMap<>();
        if (plano.getSemanas() != null) {
            for (SemanaPlanoIAResponseDTO semana : plano.getSemanas()) {
                if (semana == null || semana.getNumeroSemana() == null) {
                    logger.warn("Plano completo IA: semana sem número ignorada.");
                    continue;
                }

                int numero = semana.getNumeroSemana();
                if (numero < 1 || numero > duracaoEsperada) {
                    logger.info("Plano completo IA: semana extra descartada={}", numero);
                    continue;
                }

                SemanaPlanoIAResponseDTO anterior =
                        semanasPorNumero.putIfAbsent(numero, semana);
                if (anterior != null) {
                    logger.warn(
                            "Plano completo IA: semana duplicada ignorada={}",
                            numero
                    );
                }
            }
        }

        if (semanasPorNumero.isEmpty()) {
            throw erroFormato("A IA não retornou nenhuma semana válida.");
        }

        List<Integer> semanasFaltantes = new ArrayList<>();
        List<SemanaPlanoIAResponseDTO> semanasNormalizadas = new ArrayList<>();
        for (int numero = 1; numero <= duracaoEsperada; numero++) {
            SemanaPlanoIAResponseDTO semana = semanasPorNumero.get(numero);
            if (semana == null) {
                semanasFaltantes.add(numero);
                continue;
            } else {
                semana.setNumeroSemana(numero);
                semana.setTreinos(normalizarTreinos(
                        semana.getTreinos(),
                        "semana " + numero
                ));
            }
            semanasNormalizadas.add(semana);
        }

        if (!semanasFaltantes.isEmpty()) {
            logger.warn(
                    "Plano completo IA: semanas faltantes sem normalização segura={}",
                    semanasFaltantes
            );
            throw erroFormato("A IA retornou um plano com semanas faltantes.");
        }

        semanasNormalizadas.sort(Comparator.comparing(SemanaPlanoIAResponseDTO::getNumeroSemana));
        plano.setDuracaoSemanas(duracaoEsperada);
        plano.setSemanas(semanasNormalizadas);

        logger.info(
                "Plano completo IA: quantidade final após normalização={}",
                semanasNormalizadas.size()
        );
    }

    private List<TreinoPlanoIAResponseDTO> normalizarTreinos(
            List<TreinoPlanoIAResponseDTO> treinos,
            String contexto) {
        Map<String, TreinoPlanoIAResponseDTO> porDia = new LinkedHashMap<>();
        if (treinos != null) {
            for (TreinoPlanoIAResponseDTO treino : treinos) {
                if (treino == null || !StringUtils.hasText(treino.getDiaSemana())) {
                    logger.warn("Plano completo IA: treino sem dia ignorado em {}.", contexto);
                    continue;
                }

                String diaNormalizado = normalizar(treino.getDiaSemana());
                if (!diaEsperado(diaNormalizado)) {
                    logger.warn(
                            "Plano completo IA: dia fora do esperado ignorado em {}: {}",
                            contexto,
                            treino.getDiaSemana()
                    );
                    continue;
                }

                TreinoPlanoIAResponseDTO anterior = porDia.putIfAbsent(
                        diaNormalizado,
                        treino
                );
                if (anterior != null) {
                    logger.warn(
                            "Plano completo IA: dia duplicado ignorado em {}: {}",
                            contexto,
                            treino.getDiaSemana()
                    );
                }
            }
        }

        List<TreinoPlanoIAResponseDTO> ordenados = new ArrayList<>();
        List<String> diasPreenchidos = new ArrayList<>();
        for (String dia : DIAS_SEMANA) {
            String chave = normalizar(dia);
            TreinoPlanoIAResponseDTO treino = porDia.get(chave);
            if (treino == null) {
                treino = criarDescanso(dia);
                diasPreenchidos.add(dia);
            } else {
                treino.setDiaSemana(dia);
            }
            ordenados.add(treino);
        }

        logger.info(
                "Plano completo IA: dias preenchidos em {}={}",
                contexto,
                diasPreenchidos
        );
        return ordenados;
    }

    private TreinoPlanoIAResponseDTO criarDescanso(String diaSemana) {
        TreinoPlanoIAResponseDTO treino = new TreinoPlanoIAResponseDTO();
        treino.setDiaSemana(diaSemana);
        treino.setTitulo("Descanso");
        treino.setTipo("Descanso");
        treino.setDescricao("Dia reservado para recuperação.");
        treino.setDistanciaKm("0 km");
        treino.setDuracaoEstimada("Livre");
        treino.setPaceSugerido("Não se aplica");
        treino.setObservacoes("Recuperação para manter consistência no plano.");
        return treino;
    }

    private boolean diaEsperado(String diaNormalizado) {
        return DIAS_SEMANA.stream()
                .map(this::normalizar)
                .anyMatch(dia -> dia.equals(diaNormalizado));
    }

    private GerarTreinoIAException erroFormato(String detalhe) {
        logger.warn("Plano completo rejeitado: {}", detalhe);
        return new GerarTreinoIAException(
                BAD_GATEWAY,
                detalhe + " Tente gerar novamente."
        );
    }

    private String normalizar(String valor) {
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }

    private String sanitizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "<vazio>";
        }
        String seguro = valor
                .replaceAll("(?i)Bearer\\s+[^\\s\\\"']+", "Bearer [REDACTED]")
                .replaceAll("sk-[A-Za-z0-9_-]{10,}", "[REDACTED_API_KEY]");
        return seguro.length() > 4000
                ? seguro.substring(0, 4000) + "... [truncado]"
                : seguro;
    }
}

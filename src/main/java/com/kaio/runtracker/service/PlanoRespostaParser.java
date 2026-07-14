package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.PlanoSemanalIAResponseDTO;
import com.kaio.runtracker.dto.TreinoSemanalIAResponseDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class PlanoRespostaParser {

    private static final Logger logger =
            LoggerFactory.getLogger(PlanoRespostaParser.class);

    private static final List<String> DIAS_SEMANA = List.of(
            "segunda-feira", "ter\u00e7a-feira", "quarta-feira", "quinta-feira",
            "sexta-feira", "s\u00e1bado", "domingo"
    );

    private static final Map<String, String> NOMENCLATURAS_TREINO = Map.ofEntries(
            Map.entry("corrida continua", "Corrida cont\u00ednua"),
            Map.entry("corida continua", "Corrida cont\u00ednua"),
            Map.entry("corrrida continua", "Corrida cont\u00ednua"),
            Map.entry("corria continua", "Corrida cont\u00ednua"),
            Map.entry("rodagem", "Corrida cont\u00ednua"),
            Map.entry("corrida leve", "Corrida cont\u00ednua"),
            Map.entry("corrida longa", "Corrida longa"),
            Map.entry("corida longa", "Corrida longa"),
            Map.entry("corrrida longa", "Corrida longa"),
            Map.entry("corria longa", "Corrida longa"),
            Map.entry("longao", "Corrida longa"),
            Map.entry("treino longo", "Corrida longa"),
            Map.entry("treino de velocidade", "Treino de velocidade"),
            Map.entry("velocidade", "Treino de velocidade"),
            Map.entry("velocidadee", "Treino de velocidade"),
            Map.entry("tiro", "Treino de velocidade"),
            Map.entry("tiros", "Treino de velocidade"),
            Map.entry("treino de resistencia", "Treino de resist\u00eancia"),
            Map.entry("resistencia", "Treino de resist\u00eancia"),
            Map.entry("corrida de resistencia", "Treino de resist\u00eancia"),
            Map.entry("intervalado", "Intervalado"),
            Map.entry("interbalado", "Intervalado"),
            Map.entry("intervalada", "Intervalado"),
            Map.entry("treino intervalado", "Intervalado"),
            Map.entry("fartlek", "Fartlek"),
            Map.entry("recuperacao ativa", "Recupera\u00e7\u00e3o ativa"),
            Map.entry("recuperacao", "Recupera\u00e7\u00e3o ativa"),
            Map.entry("mobilidade", "Mobilidade"),
            Map.entry("mobilidadee", "Mobilidade"),
            Map.entry("fortalecimento", "Fortalecimento"),
            Map.entry("fortalescimento", "Fortalecimento"),
            Map.entry("fortalecimento leve", "Fortalecimento"),
            Map.entry("descanso", "Descanso"),
            Map.entry("regenerativo", "Regenerativo"),
            Map.entry("corrida regenerativa", "Regenerativo")
    );

    private final ObjectMapper objectMapper;

    public PlanoRespostaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanoSemanalIAResponseDTO parsePlanoSemanal(String content) {
        try {
            PlanoSemanalIAResponseDTO plano =
                    objectMapper.readValue(content, PlanoSemanalIAResponseDTO.class);
            normalizarEOrdenarTreinos(plano);
            return plano;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            logger.error(
                    "JSON semanal inválido: class={}, message={}",
                    exception.getClass().getName(),
                    sanitizar(exception.getMessage())
            );
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "A IA retornou um plano semanal em formato inválido. Tente novamente.",
                    exception
            );
        }
    }

    private void normalizarEOrdenarTreinos(PlanoSemanalIAResponseDTO plano) {
        if (plano == null) {
            throw erroFormato("A IA retornou um plano semanal vazio.");
        }

        int quantidadeOriginal = plano.getTreinos() == null
                ? 0
                : plano.getTreinos().size();
        List<String> diasRetornados = plano.getTreinos() == null
                ? List.of()
                : plano.getTreinos().stream()
                        .filter(treino -> treino != null
                                && StringUtils.hasText(treino.getDiaSemana()))
                        .map(TreinoSemanalIAResponseDTO::getDiaSemana)
                        .toList();
        logger.info(
                "Plano semanal IA: quantidade de treinos retornada antes da normalização={}",
                quantidadeOriginal
        );
        logger.info("Plano semanal IA: dias retornados pela IA={}", diasRetornados);

        Map<String, TreinoSemanalIAResponseDTO> porDia = new LinkedHashMap<>();
        if (plano.getTreinos() != null) {
            for (TreinoSemanalIAResponseDTO treino : plano.getTreinos()) {
                if (treino == null || !StringUtils.hasText(treino.getDiaSemana())) {
                    logger.warn("Plano semanal IA: treino sem dia da semana ignorado.");
                    continue;
                }

                String diaNormalizado = normalizar(treino.getDiaSemana());
                if (!diaEsperado(diaNormalizado)) {
                    logger.warn(
                            "Plano semanal IA: dia fora do intervalo esperado ignorado={}",
                            treino.getDiaSemana()
                    );
                    continue;
                }

                TreinoSemanalIAResponseDTO anterior = porDia.putIfAbsent(
                        diaNormalizado,
                        treino
                );
                if (anterior != null) {
                    logger.warn(
                            "Plano semanal IA: treino duplicado para o dia {} ignorado.",
                            treino.getDiaSemana()
                    );
                }
            }
        }

        List<String> diasFaltantesPreenchidos = new ArrayList<>();
        List<TreinoSemanalIAResponseDTO> ordenados = new ArrayList<>();
        for (String dia : DIAS_SEMANA) {
            String diaNormalizado = normalizar(dia);
            TreinoSemanalIAResponseDTO treino = porDia.get(diaNormalizado);
            if (treino == null) {
                treino = criarDescanso(dia);
                diasFaltantesPreenchidos.add(dia);
            } else {
                treino.setDiaSemana(dia);
            }
            normalizarNomenclaturas(treino);
            ordenados.add(treino);
        }

        logger.info(
                "Plano semanal IA: dias faltantes preenchidos automaticamente={}",
                diasFaltantesPreenchidos
        );
        logger.info(
                "Plano semanal IA: quantidade final após normalização={}",
                ordenados.size()
        );

        if (ordenados.size() != DIAS_SEMANA.size()
                || ordenados.stream().anyMatch(treino -> treino == null
                || !StringUtils.hasText(treino.getDiaSemana()))) {
            throw erroFormato(
                    "Não foi possível normalizar o plano semanal para os sete dias."
            );
        }

        plano.setTreinos(ordenados);
    }

    private boolean diaEsperado(String diaNormalizado) {
        return DIAS_SEMANA.stream()
                .map(this::normalizar)
                .anyMatch(dia -> dia.equals(diaNormalizado));
    }

    private void normalizarNomenclaturas(TreinoSemanalIAResponseDTO treino) {
        if (treino == null) {
            return;
        }

        treino.setTipo(normalizarCategoriaConhecida(treino.getTipo()));
        treino.setTitulo(normalizarCategoriaConhecida(treino.getTitulo()));
    }

    private String normalizarCategoriaConhecida(String valor) {
        if (!StringUtils.hasText(valor)) {
            return valor;
        }

        String chave = normalizar(valor);
        return NOMENCLATURAS_TREINO.getOrDefault(chave, valor.trim());
    }

    private TreinoSemanalIAResponseDTO criarDescanso(String diaSemana) {
        TreinoSemanalIAResponseDTO treino = new TreinoSemanalIAResponseDTO();
        treino.setDiaSemana(diaSemana);
        treino.setTipo("Descanso");
        treino.setTitulo("Descanso");
        treino.setDescricao("Dia reservado para recuperação.");
        treino.setDistanciaKm("0 km");
        treino.setDuracaoEstimada("Livre");
        treino.setPaceSugerido("Não se aplica");
        treino.setObservacoes("Recuperação para manter consistência no plano.");
        return treino;
    }

    private GerarTreinoIAException erroFormato(String detalhe) {
        logger.warn("Plano semanal rejeitado: {}", detalhe);
        return new GerarTreinoIAException(
                BAD_GATEWAY,
                detalhe + " Tente gerar novamente.");
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

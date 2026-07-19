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
    private static final Map<String, String> ALIASES_DIAS = Map.ofEntries(
            Map.entry("seg", "segunda-feira"),
            Map.entry("segunda", "segunda-feira"),
            Map.entry("segunda feira", "segunda-feira"),
            Map.entry("segunda-feira", "segunda-feira"),
            Map.entry("ter", "terça-feira"),
            Map.entry("terca", "terça-feira"),
            Map.entry("terca feira", "terça-feira"),
            Map.entry("terca-feira", "terça-feira"),
            Map.entry("qua", "quarta-feira"),
            Map.entry("quarta", "quarta-feira"),
            Map.entry("quarta feira", "quarta-feira"),
            Map.entry("quarta-feira", "quarta-feira"),
            Map.entry("qui", "quinta-feira"),
            Map.entry("quinta", "quinta-feira"),
            Map.entry("quinta feira", "quinta-feira"),
            Map.entry("quinta-feira", "quinta-feira"),
            Map.entry("sex", "sexta-feira"),
            Map.entry("sexta", "sexta-feira"),
            Map.entry("sexta feira", "sexta-feira"),
            Map.entry("sexta-feira", "sexta-feira"),
            Map.entry("sab", "sábado"),
            Map.entry("sabado", "sábado"),
            Map.entry("sábado", "sábado"),
            Map.entry("dom", "domingo"),
            Map.entry("domingo", "domingo")
    );

    private final ObjectMapper objectMapper;

    public PlanoTreinoRespostaParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public PlanoTreinoIAResponseDTO parsePlanoTreino(
            String content,
            int duracaoEsperada) {
        return parsePlanoTreino(content, duracaoEsperada, List.of());
    }

    public PlanoTreinoIAResponseDTO parsePlanoTreino(
            String content,
            int duracaoEsperada,
            List<String> diasDisponiveis) {
        return parsePlanoTreino(content, duracaoEsperada, diasDisponiveis, false);
    }

    public PlanoTreinoIAResponseDTO parsePlanoTreino(
            String content,
            int duracaoEsperada,
            List<String> diasDisponiveis,
            boolean possuiProva) {
        return parsePlanoTreino(
                content, duracaoEsperada, diasDisponiveis, possuiProva, null);
    }

    public PlanoTreinoIAResponseDTO parsePlanoTreino(
            String content,
            int duracaoEsperada,
            List<String> diasDisponiveis,
            boolean possuiProva,
            String diaLongao) {
        try {
            PlanoTreinoIAResponseDTO plano =
                    objectMapper.readValue(content, PlanoTreinoIAResponseDTO.class);
            normalizarPlano(
                    plano, duracaoEsperada, diasDisponiveis, possuiProva, diaLongao);
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

    private void normalizarPlano(
            PlanoTreinoIAResponseDTO plano,
            int duracaoEsperada,
            List<String> diasDisponiveis,
            boolean possuiProva,
            String diaLongao) {
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
                        "semana " + numero,
                        diasDisponiveis,
                        possuiProva,
                        diaLongao
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
            String contexto,
            List<String> diasDisponiveis,
            boolean possuiProva,
            String diaLongao) {
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

        validarTreinosNosDiasDisponiveis(
                ordenados, diasDisponiveis, contexto, possuiProva, diaLongao);

        logger.info(
                "Plano completo IA: dias preenchidos em {}={}",
                contexto,
                diasPreenchidos
        );
        return ordenados;
    }

    private void validarTreinosNosDiasDisponiveis(
            List<TreinoPlanoIAResponseDTO> treinos,
            List<String> diasDisponiveis,
            String contexto,
            boolean possuiProva,
            String diaLongao) {
        if (diasDisponiveis == null || diasDisponiveis.isEmpty()) {
            return;
        }

        List<String> diasDisponiveisNormalizados = diasDisponiveis.stream()
                .filter(StringUtils::hasText)
                .map(this::normalizarDia)
                .distinct()
                .toList();

        if (diasDisponiveisNormalizados.isEmpty()) {
            return;
        }

        boolean provaForaDosDiasDisponiveis = possuiProva && treinos.stream()
                .anyMatch(treino -> {
                    String dia = normalizarDia(treino.getDiaSemana());
                    boolean diaDisponivel = diasDisponiveisNormalizados.contains(dia);
                    return !diaDisponivel
                            && ehTreinoCorrida(treino)
                            && ehProvaOuCompeticao(treino);
                });
        boolean diaDisponivelSemCorridaCompensadoPorProva = false;
        long treinosCorridaEmDiasDisponiveis = 0;
        for (TreinoPlanoIAResponseDTO treino : treinos) {
            String dia = normalizarDia(treino.getDiaSemana());
            boolean diaDisponivel = diasDisponiveisNormalizados.contains(dia);
            boolean treinoCorrida = ehTreinoCorrida(treino);
            boolean provaOuCompeticao = ehProvaOuCompeticao(treino);

            if (treinoCorrida && !provaOuCompeticao) {
                validarEstruturaSessao(treino, contexto);
            }

            if (diaDisponivel && !treinoCorrida) {
                if (provaForaDosDiasDisponiveis && !diaDisponivelSemCorridaCompensadoPorProva) {
                    diaDisponivelSemCorridaCompensadoPorProva = true;
                    continue;
                }
                logger.warn(
                        "Plano completo IA: dia disponivel sem corrida em {}: {}",
                        contexto,
                        treino.getDiaSemana()
                );
                throw erroFormato("A IA retornou menos treinos de corrida do que os dias escolhidos.");
            }

            if (!diaDisponivel && treinoCorrida) {
                if (possuiProva && provaOuCompeticao) {
                    continue;
                }
                logger.warn(
                        "Plano completo IA: corrida em dia nao selecionado em {}: {}",
                        contexto,
                        treino.getDiaSemana()
                );
                throw erroFormato("A IA retornou corrida em dia nao selecionado.");
            }

            if (diaDisponivel) {
                treinosCorridaEmDiasDisponiveis++;
            }
        }

        long quantidadeEsperada = diasDisponiveisNormalizados.size();
        if (provaForaDosDiasDisponiveis) {
            quantidadeEsperada -= 1;
        }

        if (treinosCorridaEmDiasDisponiveis < quantidadeEsperada) {
            throw erroFormato("A IA retornou quantidade de treinos diferente dos dias escolhidos.");
        }

        validarVariedadeTreinos(treinos, contexto, diaLongao);
    }

    private void validarVariedadeTreinos(
            List<TreinoPlanoIAResponseDTO> treinos,
            String contexto,
            String diaLongao) {
        List<TreinoPlanoIAResponseDTO> corridas = treinos.stream()
                .filter(this::ehTreinoCorrida)
                .filter(treino -> !ehProvaOuCompeticao(treino))
                .toList();

        long intervalados = corridas.stream().filter(this::ehIntervalado).count();
        if (intervalados > 1) {
            logger.warn("Plano completo IA: excesso de intervalados em {}: {}", contexto, intervalados);
            throw erroFormato("A IA retornou mais de um treino intervalado na mesma semana.");
        }

        boolean possuiEducativos = corridas.stream()
                .map(this::textoCompleto)
                .anyMatch(texto -> texto.contains("educativo"));
        if (possuiEducativos) {
            throw erroFormato("A IA retornou educativos, que não foram solicitados.");
        }

        if (StringUtils.hasText(diaLongao)) {
            String diaLongaoNormalizado = normalizarDia(diaLongao);
            boolean longaoNoDiaEscolhido = corridas.stream()
                    .anyMatch(treino -> normalizarDia(treino.getDiaSemana())
                            .equals(diaLongaoNormalizado) && ehLongao(treino));
            if (!longaoNoDiaEscolhido) {
                throw erroFormato("A IA não colocou o longão no dia escolhido.");
            }
        }
    }

    private boolean ehIntervalado(TreinoPlanoIAResponseDTO treino) {
        String texto = textoCompleto(treino);
        return texto.contains("interval")
                || texto.contains("tiro")
                || texto.contains("velocidade");
    }

    private boolean ehLongao(TreinoPlanoIAResponseDTO treino) {
        String texto = textoCompleto(treino);
        return texto.contains("longao") || texto.contains("corrida longa");
    }

    private String textoCompleto(TreinoPlanoIAResponseDTO treino) {
        return normalizar(String.join(
                " ",
                valorTexto(treino.getTipo()),
                valorTexto(treino.getTitulo()),
                valorTexto(treino.getDescricao()),
                valorTexto(treino.getObservacoes())
        ));
    }

    private void validarEstruturaSessao(
            TreinoPlanoIAResponseDTO treino,
            String contexto) {
        String descricao = normalizar(valorTexto(treino.getDescricao()));
        boolean possuiEtapas = descricao.contains("aquecimento:")
                && descricao.contains("principal:")
                && descricao.contains("desaquecimento:");
        boolean possuiValorNumerico = descricao.matches(".*\\d+.*");
        boolean possuiPaceNoAquecimento = trechoEntre(
                descricao,
                "aquecimento:",
                "principal:"
        ).contains("min/km");
        boolean possuiPaceNoDesaquecimento = trechoApos(
                descricao,
                "desaquecimento:"
        ).contains("min/km");

        if (!possuiEtapas
                || !possuiValorNumerico
                || !possuiPaceNoAquecimento
                || !possuiPaceNoDesaquecimento) {
            logger.warn(
                    "Plano completo IA: sessão sem detalhamento em {}: dia={}",
                    contexto,
                    treino.getDiaSemana()
            );
            throw erroFormato(
                    "A IA retornou treino sem aquecimento, bloco principal e desaquecimento detalhados com pace."
            );
        }
    }

    private String trechoEntre(String texto, String inicio, String fim) {
        int indiceInicio = texto.indexOf(inicio);
        int indiceFim = texto.indexOf(fim);
        if (indiceInicio < 0 || indiceFim <= indiceInicio) {
            return "";
        }
        return texto.substring(indiceInicio + inicio.length(), indiceFim);
    }

    private String trechoApos(String texto, String inicio) {
        int indiceInicio = texto.indexOf(inicio);
        if (indiceInicio < 0) {
            return "";
        }
        return texto.substring(indiceInicio + inicio.length());
    }

    private boolean ehProvaOuCompeticao(TreinoPlanoIAResponseDTO treino) {
        String texto = normalizar(String.join(
                " ",
                valorTexto(treino.getTipo()),
                valorTexto(treino.getTitulo()),
                valorTexto(treino.getDescricao()),
                valorTexto(treino.getObservacoes())
        ));

        return texto.contains("prova")
                || texto.contains("competicao")
                || texto.contains("corrida alvo")
                || texto.contains("dia da prova");
    }

    private boolean ehTreinoCorrida(TreinoPlanoIAResponseDTO treino) {
        String categoria = normalizar(String.join(
                " ",
                valorTexto(treino.getTipo()),
                valorTexto(treino.getTitulo())
        ));
        String texto = normalizar(String.join(
                " ",
                valorTexto(treino.getTipo()),
                valorTexto(treino.getTitulo()),
                valorTexto(treino.getDescricao())
        ));

        if (categoria.contains("descanso")
                || categoria.contains("fortalecimento")
                || categoria.contains("mobilidade")
                || categoria.contains("alongamento")
                || categoria.contains("caminhada")) {
            return false;
        }

        return temDistanciaValida(treino.getDistanciaKm())
                || texto.contains("corrida")
                || texto.contains("rodagem")
                || texto.contains("longao")
                || texto.contains("interval")
                || texto.contains("fartlek")
                || texto.contains("ritmo")
                || texto.contains("tempo")
                || texto.contains("velocidade")
                || texto.contains("resistencia")
                || texto.contains("regenerativo")
                || texto.contains("tiro")
                || texto.contains("prova")
                || texto.contains("competicao");
    }

    private boolean temDistanciaValida(String valor) {
        if (!StringUtils.hasText(valor)) {
            return false;
        }

        String texto = normalizar(valor)
                .replace(",", ".");
        if (texto.equals("nao se aplica") || texto.equals("0") || texto.equals("0 km")) {
            return false;
        }

        java.util.regex.Matcher distancia =
                java.util.regex.Pattern.compile("^(\\d+(?:\\.\\d+)?)\\s*(?:km)?$")
                        .matcher(texto);

        return distancia.matches() && Double.parseDouble(distancia.group(1)) > 0;
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

    private String normalizarDia(String valor) {
        String diaNormalizado = normalizar(valor);
        return ALIASES_DIAS.getOrDefault(diaNormalizado, diaNormalizado);
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

    private String valorTexto(String valor) {
        return StringUtils.hasText(valor) ? valor : "";
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

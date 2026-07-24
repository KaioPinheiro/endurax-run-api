package com.kaio.runtracker.ai.agent;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.SemanaPlanoIAResponseDTO;
import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class TrainingPlanValidator {
    private static final Pattern NUMERO = Pattern.compile("(\\d+(?:[.,]\\d+)?)");
    private static final Map<String, Integer> ORDEM_DIAS = criarOrdemDias();
    private static final Set<String> TIPOS_INTENSOS =
            Set.of("INTERVALADO", "TEMPO", "RITMO", "FARTLEK", "PROVA");
    private static final Set<String> TIPOS_LEVES =
            Set.of("LEVE", "REGENERATIVO");

    public ValidationResult validate(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (plano == null) {
            errors.add("Global: o plano está ausente.");
            return new ValidationResult(errors, warnings);
        }

        validarEstruturaGlobal(plano, context, errors);
        List<SemanaPlanoIAResponseDTO> semanas =
                plano.getSemanas() == null ? List.of() : plano.getSemanas();
        for (SemanaPlanoIAResponseDTO semana : semanas) {
            validarSemana(semana, context, errors, warnings);
        }
        validarProva(plano, context, errors, warnings);
        validarProgressao(semanas, context, errors, warnings);
        return new ValidationResult(errors, warnings);
    }

    private void validarEstruturaGlobal(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context,
            List<String> errors) {
        List<SemanaPlanoIAResponseDTO> semanas = plano.getSemanas();
        int quantidade = semanas == null ? 0 : semanas.size();
        if (plano.getDuracaoSemanas() == null
                || plano.getDuracaoSemanas() != context.duracaoSemanas()) {
            errors.add("Global: duração declarada no plano diferente da duração definida.");
        }
        if (quantidade != context.duracaoSemanas()) {
            errors.add("Global: quantidade de semanas diferente da duração definida pelo sistema.");
        }
        if (!Boolean.TRUE.equals(context.request().getPossuiProva())
                && quantidade < 4) {
            errors.add("Global: plano sem prova deve possuir pelo menos 4 semanas.");
        }
        if (quantidade > 6 || context.duracaoSemanas() > 6) {
            errors.add("Global: o plano não pode ultrapassar 6 semanas.");
        }

        Set<Integer> numeros = new HashSet<>();
        int esperado = 1;
        if (semanas != null) {
            for (SemanaPlanoIAResponseDTO semana : semanas) {
                Integer numero = semana == null ? null : semana.getNumeroSemana();
                if (numero == null || numero != esperado) {
                    errors.add("Global: semanas ausentes ou fora de ordem; esperada semana "
                            + esperado + ".");
                }
                if (numero != null && !numeros.add(numero)) {
                    errors.add("Global: semana " + numero + " está duplicada.");
                }
                esperado++;
            }
        }
    }

    private void validarSemana(
            SemanaPlanoIAResponseDTO semana,
            AgentExecutionContext context,
            List<String> errors,
            List<String> warnings) {
        if (semana == null || semana.getNumeroSemana() == null) {
            errors.add("Semana não identificada: estrutura obrigatória ausente.");
            return;
        }
        int numero = semana.getNumeroSemana();
        String prefixo = "Semana " + numero + ": ";
        List<TreinoPlanoIAResponseDTO> treinos =
                semana.getTreinos() == null ? List.of() : semana.getTreinos();
        Set<String> diasPermitidos = normalizarDias(context.request().getDiasDisponiveis());
        long quantidadeTreinos = treinos.stream()
                .filter(treino -> treino != null
                        && !"DESCANSO".equals(normalizarTipo(
                                treino.getTipo(), treino.getTitulo())))
                .count();

        if (quantidadeTreinos != diasPermitidos.size()) {
            errors.add(prefixo + "quantidade de treinos diferente da quantidade de dias selecionados.");
        }

        Set<String> diasUsados = new HashSet<>();
        List<TreinoComOrdem> intensos = new ArrayList<>();
        int intervalados = 0;
        boolean possuiLeve = false;
        boolean possuiLongao = false;

        for (TreinoPlanoIAResponseDTO treino : treinos) {
            if (treino == null) {
                errors.add(prefixo + "possui treino vazio.");
                continue;
            }
            String dia = normalizarDia(treino.getDiaSemana());
            String tipo = normalizarTipo(treino.getTipo(), treino.getTitulo());
            boolean descanso = "DESCANSO".equals(tipo);
            boolean prova = "PROVA".equals(tipo);
            String identificador = StringUtils.hasText(treino.getDiaSemana())
                    ? treino.getDiaSemana() : "dia não informado";

            if (!StringUtils.hasText(treino.getDiaSemana())
                    || !StringUtils.hasText(treino.getTitulo())
                    || !StringUtils.hasText(treino.getTipo())
                    || !StringUtils.hasText(treino.getDescricao())) {
                errors.add(prefixo + identificador + " possui campos obrigatórios vazios.");
            }
            if (!descanso && !prova && !diasPermitidos.contains(dia)) {
                errors.add(prefixo + identificador + " não é um dia selecionado.");
            }
            if (!descanso && !diasUsados.add(dia)) {
                errors.add(prefixo + identificador + " possui mais de um treino no mesmo dia.");
            }

            if ("INTERVALADO".equals(tipo)) {
                intervalados++;
            }
            if (TIPOS_INTENSOS.contains(tipo)) {
                intensos.add(new TreinoComOrdem(ordemDia(dia), identificador));
            }
            possuiLeve |= TIPOS_LEVES.contains(tipo);
            possuiLongao |= "LONGAO".equals(tipo);

            OptionalDouble distancia = distancia(treino.getDistanciaKm());
            if (!"DESCANSO".equals(tipo)
                    && (distancia.isEmpty() || distancia.getAsDouble() <= 0)) {
                errors.add(prefixo + identificador + " possui distância inválida.");
            }
            if ("DESCANSO".equals(tipo) && distancia.isPresent()
                    && distancia.getAsDouble() > 0) {
                warnings.add(prefixo + identificador + " é descanso, mas possui distância.");
            }
            if (TIPOS_INTENSOS.contains(tipo)
                    && Boolean.TRUE.equals(context.request().getPossuiLesao())) {
                errors.add(prefixo + identificador
                        + " possui treino intenso incompatível com lesão ativa.");
            }
            if (!StringUtils.hasText(treino.getPaceSugerido())
                    || !StringUtils.hasText(treino.getDuracaoEstimada())) {
                warnings.add(prefixo + identificador
                        + " não permite verificar totalmente pace e duração.");
            }
            validarDataDaProva(
                    numero, dia, identificador, tipo, context, errors);
        }

        if (intervalados > 1) {
            errors.add(prefixo + "possui mais de um treino intervalado.");
        }
        if (intensos.size() > 2) {
            errors.add(prefixo + "possui mais de dois treinos intensos.");
        }
        intensos.sort(Comparator.comparingInt(TreinoComOrdem::ordem));
        for (int indice = 1; indice < intensos.size(); indice++) {
            if (intensos.get(indice).ordem() - intensos.get(indice - 1).ordem() == 1) {
                errors.add(prefixo + "possui treinos intensos em dias consecutivos ("
                        + intensos.get(indice - 1).dia() + " e "
                        + intensos.get(indice).dia() + ").");
            }
        }
        if (!possuiLeve) {
            errors.add(prefixo + "não possui treino leve ou regenerativo.");
        }
        if (objetivoExigeLongao(context.request()) && !possuiLongao) {
            errors.add(prefixo + "não possui longão compatível com o objetivo.");
        }
    }

    private void validarDataDaProva(
            int numeroSemana,
            String dia,
            String identificador,
            String tipo,
            AgentExecutionContext context,
            List<String> errors) {
        LocalDate dataProva = context.request().getDataProva();
        if (!Boolean.TRUE.equals(context.request().getPossuiProva())
                || dataProva == null || !ORDEM_DIAS.containsKey(dia)) {
            return;
        }
        LocalDate primeiraSegunda = context.dataInicio()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate dataTreino = primeiraSegunda
                .plusWeeks(numeroSemana - 1L)
                .plusDays(ORDEM_DIAS.get(dia));
        long diasDepoisDaProva = java.time.temporal.ChronoUnit.DAYS.between(
                dataProva, dataTreino);
        if (diasDepoisDaProva > 0
                && diasDepoisDaProva <= 7
                && TIPOS_INTENSOS.contains(tipo)) {
            errors.add("Semana " + numeroSemana + ": " + identificador
                    + " possui treino intenso imediatamente após a prova.");
        }
    }

    private void validarProva(
            PlanoTreinoIAResponseDTO plano,
            AgentExecutionContext context,
            List<String> errors,
            List<String> warnings) {
        if (!provaProxima(context)) {
            return;
        }
        warnings.add("Global: a prova está a menos de quatro semanas; "
                + "o ciclo deve combinar preparação curta e recuperação.");
        if (!StringUtils.hasText(plano.getAlerta())) {
            errors.add("Global: plano para prova próxima deve conter alerta sobre o prazo insuficiente.");
        }

        LocalDate dataProva = context.request().getDataProva();
        boolean provaNaData = plano.getSemanas() != null
                && plano.getSemanas().stream()
                .filter(semana -> semana != null && semana.getNumeroSemana() != null)
                .flatMap(semana -> semana.getTreinos() == null
                        ? java.util.stream.Stream.empty()
                        : semana.getTreinos().stream()
                                .map(treino -> new TreinoNaSemana(
                                        semana.getNumeroSemana(), treino)))
                .anyMatch(item -> item.treino() != null
                        && "PROVA".equals(normalizarTipo(
                                item.treino().getTipo(), item.treino().getTitulo()))
                        && dataProva.equals(dataDoTreino(
                                item.numeroSemana(),
                                normalizarDia(item.treino().getDiaSemana()),
                                context)));
        if (!provaNaData) {
            errors.add("Global: a prova não está posicionada na semana e data informadas.");
        }
    }

    private boolean provaProxima(AgentExecutionContext context) {
        LocalDate dataProva = context.request().getDataProva();
        if (!Boolean.TRUE.equals(context.request().getPossuiProva()) || dataProva == null) {
            return false;
        }
        long dias = java.time.temporal.ChronoUnit.DAYS.between(
                context.dataInicio(), dataProva);
        return dias >= 0 && dias < 28;
    }

    private LocalDate dataDoTreino(
            int numeroSemana,
            String dia,
            AgentExecutionContext context) {
        if (!ORDEM_DIAS.containsKey(dia)) {
            return null;
        }
        return context.dataInicio()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                .plusWeeks(numeroSemana - 1L)
                .plusDays(ORDEM_DIAS.get(dia));
    }

    private void validarProgressao(
            List<SemanaPlanoIAResponseDTO> semanas,
            AgentExecutionContext context,
            List<String> errors,
            List<String> warnings) {
        List<Double> volumes = semanas.stream().map(this::volumeSemana).toList();
        for (int indice = 1; indice < volumes.size(); indice++) {
            double anterior = volumes.get(indice - 1);
            double atual = volumes.get(indice);
            if (anterior > 0 && atual > anterior * 1.25 && atual - anterior > 5) {
                warnings.add("Global: aumento brusco de volume entre as semanas "
                        + indice + " e " + (indice + 1) + ".");
            }
        }

        if (provaDentroDoCiclo(context) && volumes.size() >= 2) {
            double penultima = volumes.get(volumes.size() - 2);
            double ultima = volumes.get(volumes.size() - 1);
            if (penultima > 0 && ultima >= penultima) {
                warnings.add("Global: não foi identificada redução de carga antes da prova.");
            }
        }

        double limiteAtual = limiteSuperiorVolume(context.request().getVolumeSemanalAtual());
        if (!volumes.isEmpty() && limiteAtual > 0 && volumes.get(0) > limiteAtual * 1.25) {
            warnings.add("Global: volume inicial pode estar incompatível com o volume atual informado.");
        }
        if (volumes.stream().allMatch(volume -> volume == 0)) {
            errors.add("Global: não foi possível calcular o volume do plano.");
        }
    }

    private boolean provaDentroDoCiclo(AgentExecutionContext context) {
        LocalDate dataProva = context.request().getDataProva();
        if (!Boolean.TRUE.equals(context.request().getPossuiProva()) || dataProva == null) {
            return false;
        }
        LocalDate primeiraSegunda = context.dataInicio()
                .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate fimDoCiclo = primeiraSegunda
                .plusWeeks(context.duracaoSemanas())
                .minusDays(1);
        return !dataProva.isAfter(fimDoCiclo);
    }

    private double volumeSemana(SemanaPlanoIAResponseDTO semana) {
        if (semana == null || semana.getTreinos() == null) {
            return 0;
        }
        return semana.getTreinos().stream()
                .map(TreinoPlanoIAResponseDTO::getDistanciaKm)
                .map(this::distancia)
                .filter(OptionalDouble::isPresent)
                .mapToDouble(OptionalDouble::getAsDouble)
                .sum();
    }

    private OptionalDouble distancia(String valor) {
        if (!StringUtils.hasText(valor)) {
            return OptionalDouble.empty();
        }
        Matcher matcher = NUMERO.matcher(valor);
        if (!matcher.find()) {
            return OptionalDouble.empty();
        }
        try {
            return OptionalDouble.of(Double.parseDouble(matcher.group(1).replace(',', '.')));
        } catch (NumberFormatException exception) {
            return OptionalDouble.empty();
        }
    }

    private double limiteSuperiorVolume(String valor) {
        if (!StringUtils.hasText(valor)) {
            return 0;
        }
        Matcher matcher = NUMERO.matcher(valor);
        double maior = 0;
        while (matcher.find()) {
            maior = Math.max(maior, Double.parseDouble(matcher.group(1).replace(',', '.')));
        }
        return maior;
    }

    private boolean objetivoExigeLongao(GerarPlanoTreinoRequestDTO request) {
        String texto = normalizar(String.join(" ",
                valor(request.getObjetivo()),
                valor(request.getDistanciaAlvo()),
                valor(request.getDistanciaProva())));
        return texto.contains("maratona")
                || texto.matches(".*\\b(?:21|42)\\s*(?:km|k)\\b.*");
    }

    private String normalizarTipo(String tipo, String titulo) {
        String texto = normalizar(valor(tipo) + " " + valor(titulo));
        if (texto.contains("regener")) return "REGENERATIVO";
        if (texto.contains("interval") || texto.contains("tiro")) return "INTERVALADO";
        if (texto.contains("long") || texto.contains("longao")) return "LONGAO";
        if (texto.contains("fartlek")) return "FARTLEK";
        if (texto.contains("prova") || texto.contains("competicao")) return "PROVA";
        if (texto.contains("descanso")) return "DESCANSO";
        if (texto.contains("tempo")) return "TEMPO";
        if (texto.contains("ritmo")) return "RITMO";
        if (texto.contains("leve") || texto.contains("rodagem")) return "LEVE";
        return texto.toUpperCase(Locale.ROOT);
    }

    private Set<String> normalizarDias(List<String> dias) {
        Set<String> normalizados = new HashSet<>();
        if (dias != null) {
            dias.stream().map(this::normalizarDia).forEach(normalizados::add);
        }
        normalizados.remove("");
        return normalizados;
    }

    private String normalizarDia(String dia) {
        String texto = normalizar(dia).replace("-", " ");
        Map<String, String> aliases = new LinkedHashMap<>();
        aliases.put("seg", "segunda feira");
        aliases.put("segunda", "segunda feira");
        aliases.put("ter", "terca feira");
        aliases.put("terca", "terca feira");
        aliases.put("qua", "quarta feira");
        aliases.put("quarta", "quarta feira");
        aliases.put("qui", "quinta feira");
        aliases.put("quinta", "quinta feira");
        aliases.put("sex", "sexta feira");
        aliases.put("sexta", "sexta feira");
        aliases.put("sab", "sabado");
        aliases.put("dom", "domingo");
        return aliases.getOrDefault(texto, texto);
    }

    private int ordemDia(String dia) {
        return ORDEM_DIAS.getOrDefault(dia, 99);
    }

    private String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private String valor(String valor) {
        return valor == null ? "" : valor;
    }

    private static Map<String, Integer> criarOrdemDias() {
        Map<String, Integer> dias = new LinkedHashMap<>();
        dias.put("segunda feira", 0);
        dias.put("terca feira", 1);
        dias.put("quarta feira", 2);
        dias.put("quinta feira", 3);
        dias.put("sexta feira", 4);
        dias.put("sabado", 5);
        dias.put("domingo", 6);
        return Map.copyOf(dias);
    }

    private record TreinoComOrdem(int ordem, String dia) {
    }

    private record TreinoNaSemana(
            int numeroSemana,
            TreinoPlanoIAResponseDTO treino) {
    }
}

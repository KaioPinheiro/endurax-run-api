package com.kaio.runtracker.ai.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.ai.prompt.PlanoTreinoReviewPromptBuilder;
import com.kaio.runtracker.ai.prompt.PromptObjetivoFactory;
import com.kaio.runtracker.config.OpenAiClientConfig;
import com.kaio.runtracker.config.OpenAiProperties;
import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.dto.PlanoTreinoIAResponseDTO;
import com.kaio.runtracker.dto.SemanaPlanoIAResponseDTO;
import com.kaio.runtracker.dto.TreinoPlanoIAResponseDTO;
import com.kaio.runtracker.service.GerarPlanoTreinoIAService;
import com.kaio.runtracker.service.OpenAIService;
import com.kaio.runtracker.service.PlanoTreinoPromptBuilder;
import com.kaio.runtracker.service.PlanoTreinoRespostaParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class TrainingPlanAgentLiveTest {
    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_LIVE_TRAINING_PLAN_PIPELINE", matches = "(?i)true")
    void executaCiclosV1SemProvaPeloPipelineRealSemPagamentoOuPersistencia() {
        String apiKey = System.getenv("OPENAI_API_KEY");
        assertThat(apiKey).as("OPENAI_API_KEY deve estar definida para o teste live").isNotBlank();

        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        OpenAiProperties properties = propriedades(apiKey);
        OpenAIService openAIService = new OpenAIService(
                properties, objectMapper, new OpenAiClientConfig().openAiRestClient(properties));
        PlanoTreinoPromptBuilder promptBuilder =
                new PlanoTreinoPromptBuilder(new PromptObjetivoFactory());
        PlanoTreinoRespostaParser respostaParser = new PlanoTreinoRespostaParser(objectMapper);
        OpenAiTrainingPlanReviewer reviewer = new OpenAiTrainingPlanReviewer(
                openAIService, objectMapper, new PlanoTreinoReviewPromptBuilder(objectMapper));

        int repeticoes = Math.max(1, Math.min(3,
                Integer.parseInt(valorAmbiente("LIVE_TRAINING_PLAN_REPETITIONS", "1"))));
        String filtroCenarios = valorAmbiente("LIVE_TRAINING_PLAN_SCENARIOS", "");
        List<String> falhas = new ArrayList<>();
        for (Cenario cenario : cenarios().stream()
                .filter(item -> filtroCenarios.isBlank() || filtroCenarios.contains(item.id()))
                .toList()) {
            for (int repeticao = 1; repeticao <= repeticoes; repeticao++) {
                try {
                    executarCenario(cenario, repeticao, promptBuilder, respostaParser,
                            openAIService, reviewer);
                } catch (RuntimeException | AssertionError exception) {
                    String identificador = cenario.id() + "-r" + repeticao;
                    List<String> motivos = exception instanceof PlanoTreinoReprovadoException reprovado
                            ? reprovado.getErrors() : List.of(exception.getMessage());
                    falhas.add(identificador + ": " + motivos);
                    System.out.printf("LIVE_FAIL cenario=%s repeticao=%d motivos=%s%n",
                            cenario.id(), repeticao, motivos);
                }
            }
        }
        assertThat(falhas).as("Falhas live: %s", falhas).isEmpty();
    }

    private void executarCenario(
            Cenario cenario,
            int repeticao,
            PlanoTreinoPromptBuilder promptBuilder,
            PlanoTreinoRespostaParser respostaParser,
            OpenAIService openAIService,
            OpenAiTrainingPlanReviewer reviewer) {
        Clock clock = Clock.fixed(cenario.dataInicio().atStartOfDay(ZONE).toInstant(), ZONE);
        int duracao = cenario.duracaoSemanas();
        cenario.request().setDuracaoSemanas(duracao);
        GerarPlanoTreinoIAService generator = new GerarPlanoTreinoIAService(
                promptBuilder, openAIService, respostaParser, clock);
        LiveCapturingValidator validator = new LiveCapturingValidator(
                cenario.id(), repeticao);
        TrainingPlanAgent agent = new TrainingPlanAgent(generator, reviewer, validator, 2);
        AgentExecutionContext context = new AgentExecutionContext(
                cenario.request(), duracao, cenario.dataInicio(),
                cenario.id() + "-r" + repeticao);

        AgentExecutionResult resultado = agent.execute(context);

        assertThat(resultado.plano()).as(cenario.id()).isNotNull();
        assertThat(resultado.validacao().isValid()).as(cenario.id()).isTrue();
        assertThat(resultado.revisao().valid()).as(cenario.id()).isTrue();
        assertThat(resultado.plano().getSemanas()).hasSize(duracao);
        assertThat(resultado.plano().getSemanas().stream()
                .flatMap(semana -> semana.getTreinos().stream())
                .noneMatch(treino -> texto(treino.getTipo(), treino.getTitulo())
                        .matches(".*\\b(prova|competicao|competição)\\b.*")))
                .as("ciclo V1 não deve inventar competição marcada")
                .isTrue();
        System.out.printf("LIVE_OK cenario=%s repeticao=%d semanas=%d correcoes=%d%n",
                cenario.id(), repeticao, duracao, resultado.correcoesRealizadas());
    }

    private List<Cenario> cenarios() {
        LocalDate hoje = LocalDate.now(ZONE);
        return List.of(
                new Cenario("L1-nunca-correu-primeiros-5k", hoje, primeiros5Km(), 4),
                new Cenario("L2-intermediario-5k", hoje, cincoKmIntermediario(), 5),
                new Cenario("L3-intermediario-10k", hoje, dezKm(), 6),
                new Cenario("L4-intermediario-meia", hoje, meia(), 5),
                new Cenario("L5-avancado-maratona", hoje, maratona(), 6),
                new Cenario("C3-primeiros-10k", hoje, c3(), 6),
                new Cenario("C4-melhorar-5k", hoje, c4(), 6),
                new Cenario("C7-melhorar-meia", hoje, c7(), 6));
    }

    private GerarPlanoTreinoRequestDTO c3() {
        GerarPlanoTreinoRequestDTO request = base("Primeiros 10 km", "10 km",
                List.of("terça-feira", "quinta-feira", "sábado", "domingo"));
        request.setIdade(32);
        request.setExperienciaCorrida("6 meses a 1 ano");
        request.setRitmoConfortavel("6:00-6:30 min/km");
        request.setVolumeSemanalAtual("10-20 km");
        request.setDiaLongao("domingo");
        request.setDuracaoSemanas(6);
        request.setObservacoes("");
        return request;
    }

    private GerarPlanoTreinoRequestDTO c4() {
        GerarPlanoTreinoRequestDTO request = base("Melhorar tempo nos 5 km", "5 km",
                List.of("terça-feira", "quarta-feira", "sexta-feira", "domingo"));
        request.setIdade(30);
        request.setExperienciaCorrida("1 a 3 anos");
        request.setTempoAtual("25:00");
        request.setTempoDesejado("23:30");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setVolumeSemanalAtual("20-40 km");
        request.setDiaLongao("domingo");
        request.setDuracaoSemanas(6);
        request.setObservacoes("");
        return request;
    }

    private GerarPlanoTreinoRequestDTO c7() {
        GerarPlanoTreinoRequestDTO request = base("Melhorar tempo na Meia Maratona", "21 km",
                List.of("segunda-feira", "terça-feira", "quinta-feira", "sábado", "domingo"));
        request.setIdade(33);
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setTempoAtual("1:48:00");
        request.setTempoDesejado("1:42:00");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setMaiorDistanciaCorrida("21");
        request.setVolumeSemanalAtual("40-60 km");
        request.setDiaLongao("domingo");
        request.setDuracaoSemanas(6);
        request.setObservacoes("");
        return request;
    }

    @Test
    void fixturesManuaisRespeitamContratoSemChamarOpenAi() {
        try (var factory = jakarta.validation.Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            List<Cenario> manuais = cenarios().stream()
                    .filter(cenario -> cenario.id().startsWith("C")).toList();
            assertThat(manuais).extracting(Cenario::id)
                    .containsExactly("C3-primeiros-10k", "C4-melhorar-5k", "C7-melhorar-meia");
            for (Cenario cenario : manuais) {
                GerarPlanoTreinoRequestDTO request = cenario.request();
                assertThat(validator.validate(request)).as(cenario.id()).isEmpty();
                assertThat(request.getDuracaoSemanas()).isEqualTo(6);
                assertThat(request.getPossuiProva()).isFalse();
                assertThat(request.getPossuiLesao()).isFalse();
                assertThat(request.getObservacoes()).isEmpty();
                assertThat(request.getCorre5KmSemCaminhar()).isNull();
                assertThat(request.getTempo5Km()).isNull();
            }
            assertThat(c3().getTempoAtual()).isNull();
            assertThat(c3().getTempoDesejado()).isNull();
            assertThat(c3().getMaiorDistanciaCorrida()).isNull();
            assertThat(c4().getMaiorDistanciaCorrida()).isNull();
            assertThat(c7().getMaiorDistanciaCorrida()).isEqualTo("21");
        }
    }

    @Test
    void diagnosticoPreservaDiasPacesEBlocosEmTodasAsTentativas() {
        TreinoPlanoIAResponseDTO treino = new TreinoPlanoIAResponseDTO();
        treino.setDiaSemana("sábado");
        treino.setTipo("Intervalado");
        treino.setTitulo("Treino de diagnóstico");
        treino.setDuracaoEstimada("40 min");
        treino.setDistanciaKm("7");
        treino.setPaceSugerido("5:30-6:00 min/km");
        treino.setDescricao("Aquecimento: 10 min a 6:00-6:30 min/km | "
                + "Principal: 6 x (400 m em 2 min a 1:45-1:55 min/km + 2 min de recuperação) | "
                + "Desaquecimento: 8 min a 6:30-7:00 min/km\n");
        LiveCapturingValidator validator = new LiveCapturingValidator("C4-melhorar-5k", 1);
        for (int tentativa = 0; tentativa <= 2; tentativa++) {
            validator.tentativa = tentativa;
            String etapa = tentativa == 0 ? "GENERATION" : "CORRECTION_" + tentativa;
            assertThat(validator.detalheTreino(1, treino, etapa)).contains(
                    "cenario=C4-melhorar-5k repeticao=1 tentativa=" + tentativa,
                    "etapa=" + etapa, "semana=1", "diaSemana=sábado", "tipo=Intervalado",
                    "titulo=Treino de diagnóstico", "duracaoEstimada=40 min",
                    "distanciaKm=7", "paceSugerido=5:30-6:00 min/km",
                    "nome=Aquecimento, instrucao=10 min a 6:00-6:30 min/km",
                    "nome=Principal, instrucao=6 x (400 m em 2 min a 1:45-1:55 min/km + 2 min de recuperação)",
                    "nome=Desaquecimento, instrucao=8 min a 6:30-7:00 min/km")
                    .doesNotContain("\n", "\r");
        }
    }

    private GerarPlanoTreinoRequestDTO primeiros5Km() {
        GerarPlanoTreinoRequestDTO request = base(
                "Primeiros 5 km", "5 km",
                List.of("terça-feira", "quinta-feira", "domingo"));
        request.setCorre5KmSemCaminhar(false);
        request.setExperienciaCorrida("Nunca corri");
        request.setVolumeSemanalAtual(null);
        request.setRitmoConfortavel("Caminhada / trote leve");
        request.setMaiorDistanciaCorrida("0 km");
        request.setDiaLongao("domingo");
        return request;
    }

    private GerarPlanoTreinoRequestDTO cincoKmIntermediario() {
        GerarPlanoTreinoRequestDTO request = base(
                "Primeiros 5 km", "5 km",
                List.of("terça-feira", "quinta-feira", "domingo"));
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("29:00");
        request.setExperienciaCorrida("6 meses a 1 ano");
        request.setVolumeSemanalAtual("10-20 km");
        request.setRitmoConfortavel("6:00-6:30 min/km");
        request.setMaiorDistanciaCorrida("8 km");
        request.setDiaLongao("domingo");
        return request;
    }

    private GerarPlanoTreinoRequestDTO dezKm() {
        GerarPlanoTreinoRequestDTO request = base(
                "Melhorar tempo nos 10 km", "10 km",
                List.of("terça-feira", "quinta-feira", "domingo"));
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("24:00");
        request.setTempoAtual("50:00");
        request.setTempoDesejado("47:00");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("5:30-6:00 min/km");
        request.setMaiorDistanciaCorrida("12 km");
        request.setDiaLongao("domingo");
        return request;
    }

    private GerarPlanoTreinoRequestDTO meia() {
        GerarPlanoTreinoRequestDTO request = base(
                "Primeira Meia Maratona", "21 km",
                List.of("terça-feira", "quinta-feira", "domingo"));
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("27:00");
        request.setExperienciaCorrida("1 a 3 anos");
        request.setVolumeSemanalAtual("20-40 km");
        request.setRitmoConfortavel("6:00-6:30 min/km");
        request.setMaiorDistanciaCorrida("15 km");
        request.setDiaLongao("domingo");
        return request;
    }

    private GerarPlanoTreinoRequestDTO maratona() {
        GerarPlanoTreinoRequestDTO request = base(
                "Melhorar tempo na Maratona", "42 km",
                List.of("segunda-feira", "terça-feira", "quinta-feira", "domingo"));
        request.setCorre5KmSemCaminhar(true);
        request.setTempo5Km("21:00");
        request.setTempoAtual("3:20:00");
        request.setTempoDesejado("3:10:00");
        request.setExperienciaCorrida("Mais de 3 anos");
        request.setVolumeSemanalAtual("40-60 km");
        request.setRitmoConfortavel("4:50-5:20 min/km");
        request.setMaiorDistanciaCorrida("42 km");
        request.setDiaLongao("domingo");
        return request;
    }

    private GerarPlanoTreinoRequestDTO base(
            String objetivo, String distancia, List<String> dias) {
        GerarPlanoTreinoRequestDTO request = new GerarPlanoTreinoRequestDTO();
        request.setObjetivo(objetivo);
        request.setDistanciaAlvo(distancia);
        request.setPossuiProva(false);
        request.setDataProva(null);
        request.setDiasDisponiveis(dias);
        request.setIdade(35);
        request.setPossuiLesao(false);
        return request;
    }

    private String texto(String... valores) {
        return String.join(" ", valores[0] == null ? "" : valores[0],
                valores[1] == null ? "" : valores[1]).toLowerCase();
    }

    private OpenAiProperties propriedades(String apiKey) {
        OpenAiProperties properties = new OpenAiProperties();
        properties.setApiKey(apiKey);
        properties.setModel(valorAmbiente("OPENAI_MODEL", "gpt-4o-mini"));
        properties.setBaseUrl(valorAmbiente("OPENAI_BASE_URL", "https://api.openai.com"));
        properties.setMaxOutputTokens(7000);
        properties.setConnectTimeoutMs(10000);
        properties.setReadTimeoutMs(120000);
        return properties;
    }

    private String valorAmbiente(String nome, String padrao) {
        String valor = System.getenv(nome);
        return valor == null || valor.isBlank() ? padrao : valor;
    }

    private static final class LiveCapturingValidator extends TrainingPlanValidator {
        private static final Pattern MINUTOS =
                Pattern.compile("^\\s*(\\d+)\\s+min\\s*$", Pattern.CASE_INSENSITIVE);
        private static final Pattern DISTANCIA =
                Pattern.compile("(\\d+(?:[.,]\\d+)?)");
        private final String cenario;
        private final int repeticao;
        private int tentativa;

        private LiveCapturingValidator(String cenario, int repeticao) {
            this.cenario = cenario;
            this.repeticao = repeticao;
        }

        @Override
        public ValidationResult validate(
                PlanoTreinoIAResponseDTO plano, AgentExecutionContext context) {
            ValidationResult resultado = super.validate(plano, context);
            registrarPlano(plano);
            List<Integer> longoes = longoes(plano);
            boolean deload = possuiDeload(longoes);
            boolean retomada = possuiRetomadaAposDeload(longoes);
            boolean regraDisparou = resultado.getWarnings().stream()
                    .anyMatch(aviso -> aviso.contains("o longão aumentou"));
            System.out.printf(
                    "LIVE_VALIDATION cenario=%s repeticao=%d tentativa=%d "
                            + "javaErrors=%s javaWarnings=%s longoesMin=%s "
                            + "regraLongaoDisparou=%s deload=%s retomadaAposDeload=%s%n",
                    cenario, repeticao, tentativa++, resultado.getErrors(),
                    resultado.getWarnings(), longoes, regraDisparou, deload, retomada);
            return resultado;
        }

        private void registrarPlano(PlanoTreinoIAResponseDTO plano) {
            if (plano == null || plano.getSemanas() == null) return;
            String etapa = tentativa == 0 ? "GENERATION" : "CORRECTION_" + tentativa;
            for (SemanaPlanoIAResponseDTO semana : plano.getSemanas()) {
                if (semana == null) continue;
                List<TreinoPlanoIAResponseDTO> treinos = semana.getTreinos() == null
                        ? List.of() : semana.getTreinos();
                double volume = treinos.stream().mapToDouble(this::distanciaKm).sum();
                TreinoPlanoIAResponseDTO longao = treinos.stream()
                        .filter(this::ehLongao).findFirst().orElse(null);
                List<String> sessoes = treinos.stream().map(treino -> String.format(
                        "{tipo=%s,titulo=%s,distanciaKm=%s,duracaoEstimada=%s}",
                        seguro(treino == null ? null : treino.getTipo()),
                        seguro(treino == null ? null : treino.getTitulo()),
                        seguro(treino == null ? null : treino.getDistanciaKm()),
                        seguro(treino == null ? null : treino.getDuracaoEstimada()))).toList();
                System.out.printf(
                        "LIVE_PLAN cenario=%s repeticao=%d tentativa=%d etapa=%s semana=%s volumeKm=%.1f "
                                + "longaoDistancia=%s longaoDuracao=%s sessoes=%s%n",
                        cenario, repeticao, tentativa, etapa, semana.getNumeroSemana(), volume,
                        seguro(longao == null ? null : longao.getDistanciaKm()),
                        seguro(longao == null ? null : longao.getDuracaoEstimada()), sessoes);
                for (TreinoPlanoIAResponseDTO treino : treinos) {
                    if (treino == null) continue;
                    System.out.println(detalheTreino(semana.getNumeroSemana(), treino, etapa));
                }
            }
        }

        private String detalheTreino(Integer semana, TreinoPlanoIAResponseDTO treino, String etapa) {
            // Os blocos são texto no contrato real. Preserve suas durações, distâncias,
            // repetições e paces literalmente, sem calcular ou interpretar prescrições.
            String descricao = treino.getDescricao();
            List<String> blocos = descricao == null ? List.of() :
                    java.util.Arrays.stream(descricao.split("\\|"))
                            .map(bloco -> bloco.split(":", 2))
                            .map(partes -> partes.length == 2
                                    ? "{nome=" + textoDiagnostico(partes[0])
                                            + ", instrucao=" + textoDiagnostico(partes[1]) + "}"
                                    : "{instrucao=" + textoDiagnostico(partes[0]) + "}")
                            .toList();
            return String.format(
                    "LIVE_PLAN cenario=%s repeticao=%d tentativa=%d etapa=%s semana=%s "
                            + "diaSemana=%s tipo=%s titulo=%s duracaoEstimada=%s "
                            + "distanciaKm=%s paceSugerido=%s blocos=%s",
                    cenario, repeticao, tentativa, etapa, semana,
                    textoDiagnostico(treino.getDiaSemana()), textoDiagnostico(treino.getTipo()),
                    textoDiagnostico(treino.getTitulo()), textoDiagnostico(treino.getDuracaoEstimada()),
                    textoDiagnostico(treino.getDistanciaKm()), textoDiagnostico(treino.getPaceSugerido()),
                    blocos);
        }

        private String textoDiagnostico(String valor) {
            return valor == null ? "null" : valor.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ").trim();
        }

        private double distanciaKm(TreinoPlanoIAResponseDTO treino) {
            if (treino == null || treino.getDistanciaKm() == null) return 0;
            Matcher matcher = DISTANCIA.matcher(treino.getDistanciaKm());
            if (!matcher.find()) return 0;
            try {
                return Double.parseDouble(matcher.group(1).replace(',', '.'));
            } catch (NumberFormatException exception) {
                return 0;
            }
        }

        private String seguro(String valor) {
            if (valor == null || valor.isBlank()) return "null";
            String limpo = valor.replaceAll("[\\r\\n\\t\\p{Cntrl}]+", " ").trim();
            return limpo.length() > 120 ? limpo.substring(0, 120) + "..." : limpo;
        }

        private List<Integer> longoes(PlanoTreinoIAResponseDTO plano) {
            if (plano == null || plano.getSemanas() == null) {
                return List.of();
            }
            return plano.getSemanas().stream().map(semana -> {
                if (semana == null || semana.getTreinos() == null) return null;
                return semana.getTreinos().stream()
                        .filter(this::ehLongao)
                        .map(TreinoPlanoIAResponseDTO::getDuracaoEstimada)
                        .filter(Objects::nonNull)
                        .map(MINUTOS::matcher)
                        .filter(Matcher::matches)
                        .map(matcher -> Integer.valueOf(matcher.group(1)))
                        .findFirst().orElse(null);
            }).toList();
        }

        private boolean ehLongao(TreinoPlanoIAResponseDTO treino) {
            if (treino == null) return false;
            String texto = ((treino.getTipo() == null ? "" : treino.getTipo()) + " "
                    + (treino.getTitulo() == null ? "" : treino.getTitulo()))
                    .toLowerCase(Locale.ROOT);
            return texto.contains("long");
        }

        private boolean possuiDeload(List<Integer> valores) {
            for (int i = 1; i < valores.size(); i++) {
                if (valores.get(i - 1) != null && valores.get(i) != null
                        && valores.get(i) < valores.get(i - 1)) return true;
            }
            return false;
        }

        private boolean possuiRetomadaAposDeload(List<Integer> valores) {
            for (int i = 2; i < valores.size(); i++) {
                Integer anterior = valores.get(i - 2);
                Integer deload = valores.get(i - 1);
                Integer atual = valores.get(i);
                if (anterior != null && deload != null && atual != null
                        && deload < anterior && atual > deload) return true;
            }
            return false;
        }
    }

    private record Cenario(
            String id, LocalDate dataInicio, GerarPlanoTreinoRequestDTO request,
            int duracaoSemanas) {}
}

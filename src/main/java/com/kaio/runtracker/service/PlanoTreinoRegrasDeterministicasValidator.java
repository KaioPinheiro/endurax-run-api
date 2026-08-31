package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Set;

@Component
public class PlanoTreinoRegrasDeterministicasValidator {
    static final int LEAD_MINIMO_PROVA_DIAS = 14;
    private static final String EXPERIENCIA_MENOS_6_MESES = "Menos de 6 meses";
    private static final String OBJETIVO_COMECAR_A_CORRER = "Começar a correr";
    private static final Set<String> EXPERIENCIAS_PARA_COMECAR_A_CORRER = Set.of(
            "Nunca corri",
            "Estou parado(a)");
    private static final Set<String> OBJETIVOS_MENOS_6_MESES = Set.of(
            "Melhorar condicionamento",
            "Emagrecer",
            "Primeiros 5 km",
            "Primeiros 10 km",
            "Melhorar tempo nos 5 km",
            "Melhorar tempo nos 10 km");

    private final Clock clock;

    public PlanoTreinoRegrasDeterministicasValidator() {
        this(Clock.systemDefaultZone());
    }

    PlanoTreinoRegrasDeterministicasValidator(Clock clock) {
        this.clock = clock;
    }

    public void normalizarEValidarSolicitacaoPublica(GerarPlanoTreinoRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiProva())) {
            request.setDistanciaProva(request.getDistanciaAlvo());
            LocalDate dataMinima = LocalDate.now(clock).plusDays(LEAD_MINIMO_PROVA_DIAS);
            if (request.getDataProva() == null || request.getDataProva().isBefore(dataMinima)) {
                throw new IllegalArgumentException(
                        "A prova deve estar marcada com pelo menos 14 dias de antecedência.");
            }
        }
        validarCompatibilidadeExperienciaObjetivo(request);
        validarMaratona(request);
    }

    public void prepararNovaSolicitacaoPublicaV1(GerarPlanoTreinoRequestDTO request) {
        request.setPossuiProva(false);
        request.setDataProva(null);
        request.setDistanciaProva(null);
        request.setObjetivoProva(null);
        request.setImportanciaProva(null);
        if (request.getDuracaoSemanas() == null
                || request.getDuracaoSemanas() < 4
                || request.getDuracaoSemanas() > 6) {
            throw new IllegalArgumentException(
                    "Escolha uma duração de 4, 5 ou 6 semanas.");
        }
        validarCompatibilidadeExperienciaObjetivo(request);
        validarMaratona(request);
    }

    public void validarSolicitacaoPersistidaAntesDoPix(GerarPlanoTreinoRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiProva())
                && !normalizar(request.getDistanciaAlvo())
                        .equals(normalizar(request.getDistanciaProva()))) {
            throw new IllegalArgumentException(
                    "A distância da prova deve corresponder à distância alvo selecionada.");
        }
        normalizarEValidarSolicitacaoPublica(request);
    }

    public void validarMaratona(GerarPlanoTreinoRequestDTO request) {
        if (!ehPlanoMaratona(request)) return;

        long dias = request.getDiasDisponiveis() == null ? 0
                : request.getDiasDisponiveis().stream()
                        .filter(StringUtils::hasText)
                        .map(this::normalizar)
                        .distinct()
                        .count();
        if (dias < 4) {
            throw new IllegalArgumentException(
                    "Para plano de maratona, selecione pelo menos 4 dias disponiveis para treinar.");
        }
        if (request.getIdade() == null || request.getIdade() < 18) {
            throw new IllegalArgumentException(
                    "Para plano de maratona, a idade minima e 18 anos.");
        }
        if (!volumeMaratonaPermitido(request.getVolumeSemanalAtual())) {
            throw new IllegalArgumentException(
                    "Para plano de maratona, o volume semanal atual deve ser 40-60 km, 60-80 km ou 80+ km.");
        }
        if (!experienciaMaratonaPermitida(request.getExperienciaCorrida())) {
            throw new IllegalArgumentException(
                    "Para plano de maratona, a experiencia na corrida deve ser a partir de 1 a 3 anos.");
        }
    }

    private void validarCompatibilidadeExperienciaObjetivo(
            GerarPlanoTreinoRequestDTO request) {
        if (OBJETIVO_COMECAR_A_CORRER.equals(request.getObjetivo())
                && !EXPERIENCIAS_PARA_COMECAR_A_CORRER.contains(
                        request.getExperienciaCorrida())) {
            throw new IllegalArgumentException(
                    "Escolha um objetivo compatível com sua experiência na corrida.");
        }
        if (EXPERIENCIA_MENOS_6_MESES.equals(request.getExperienciaCorrida())
                && !OBJETIVOS_MENOS_6_MESES.contains(request.getObjetivo())) {
            throw new IllegalArgumentException(
                    "Escolha um objetivo compatível com sua experiência na corrida.");
        }
    }

    private boolean ehPlanoMaratona(GerarPlanoTreinoRequestDTO request) {
        return campoIndicaMaratona(request.getObjetivo())
                || campoIndicaMaratona(request.getDistanciaAlvo())
                || campoIndicaMaratona(request.getDistanciaProva())
                || campoIndicaMaratona(request.getObjetivoProva())
                || campoIndicaMaratona(request.getObservacoes());
    }

    private boolean campoIndicaMaratona(String valor) {
        String texto = normalizar(valor);
        if (!StringUtils.hasText(texto)) return false;
        if (texto.matches(".*\\b42\\s*(km|k|quilometros?)\\b.*")) return true;
        return texto.contains("maratona")
                && !texto.contains("meia maratona")
                && !texto.contains("21 km")
                && !texto.contains("21k");
    }

    private boolean volumeMaratonaPermitido(String valor) {
        String texto = normalizar(valor).replace("–", "-").replace("—", "-")
                .replaceAll("\\s+", "");
        return texto.equals("40-60km") || texto.equals("40a60km")
                || texto.equals("40ate60km") || texto.equals("60-80km")
                || texto.equals("60a80km") || texto.equals("60ate80km")
                || texto.equals("80+km") || texto.equals("80km+");
    }

    private boolean experienciaMaratonaPermitida(String valor) {
        String texto = normalizar(valor);
        return texto.contains("1 a 3 anos") || texto.contains("1-3 anos")
                || texto.contains("mais de 3 anos") || texto.contains("mais que 3 anos")
                || texto.contains("acima de 3 anos");
    }

    private String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) return "";
        return Normalizer.normalize(valor.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }
}

package com.kaio.runtracker.ai.prompt;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.text.Normalizer;
import java.util.Locale;

@Component
public class PromptObjetivoFactory {

    public String criarPrompt(
            GerarPlanoTreinoRequestDTO request) {
        String objetivo = normalizar(request.getObjetivo());
        String distancia = normalizar(request.getDistanciaAlvo());

        if (ehMaratona(objetivo, distancia)) {
            return criarPromptPrimeiraMaratona();
        }
        if (ehDezKm(objetivo, distancia)) {
            return criarPrompt10Km();
        }
        return criarPromptBase();
    }

    public String criarPromptBase() {
        return """
                - Para qualquer objetivo que nao seja maratona, inclusive 5 km, 10 km e meia maratona, retorne alerta como string vazia.
                """;
    }

    public String criarPrompt10Km() {
        return """
                - Em objetivos de 10 km, use a faixa completa e um valor representativo coerente com experiencia e ritmo do atleta.
                %s
                """.formatted(criarPromptBase().strip());
    }

    public String criarPromptPrimeiraMaratona() {
        return """
                Para objetivos de maratona, quando o prazo ou a base atual forem insuficientes, entregue um ciclo seguro de construcao de base e explique isso com cuidado no campo alerta.

                Avaliacao de viabilidade obrigatoria para maratona:
                - Trate 4 a 6 semanas como um ciclo, nao necessariamente como a preparacao completa para qualquer objetivo.
                - Para maratona, uma meta agressiva ou um prazo curto so pode ser tratada como viavel se os dados mostrarem base recente compativel; na duvida, seja conservador.
                - Para maratona, se o objetivo exigir adaptacoes maiores do que o prazo e a base atual permitem, nao force um plano de pico nem prescreva carga irreal.
                - Para maratona insuficiente ou incerta, objetivoPlano e resumo devem dizer que este e um ciclo de construcao de base para aproximar o atleta da meta.
                - Somente para maratona insuficiente ou incerta, alerta deve explicar com empatia que o ciclo isolado nao garante nem completa a preparacao para a meta e recomendar reavaliacao ao final.
                - Para maratona com dados que sustentem o ciclo proposto, retorne alerta como string vazia. Nunca use alerta para prometer resultado.
                """;
    }

    private boolean ehMaratona(String objetivo, String distancia) {
        String texto = objetivo + " " + distancia;
        return (texto.contains("maratona")
                && !texto.contains("meia maratona"))
                || texto.matches(".*\\b42\\s*(?:km|k)\\b.*");
    }

    private boolean ehDezKm(String objetivo, String distancia) {
        return (objetivo + " " + distancia)
                .matches(".*\\b10\\s*(?:km|k)\\b.*");
    }

    private String normalizar(String valor) {
        if (!StringUtils.hasText(valor)) {
            return "";
        }
        return Normalizer.normalize(valor, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}

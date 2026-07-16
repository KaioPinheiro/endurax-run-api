package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class PlanoTreinoPromptBuilder {

    public String criarSystemPrompt() {
        return """
                Voce e o RunPace Coach, especialista em corrida de rua.
                Gere planos seguros, objetivos e em JSON valido. Nao substitua orientacao medica.
                """;
    }

    public String criarPrompt(GerarPlanoTreinoRequestDTO request, int duracaoSemanas) {
        return """
                Gere um plano completo de corrida com exatamente %d semana(s).

                Regras fixas:
                - Cada semana deve ter exatamente 7 treinos, de segunda-feira a domingo, nessa ordem.
                - Use corrida somente nos dias disponiveis informados.
                - Todos os dias disponiveis informados devem receber treino de corrida em todas as semanas.
                - A quantidade de treinos de corrida por semana deve ser exatamente igual a quantidade de dias disponiveis informados.
                - Nos demais dias, use descanso, mobilidade, recuperacao ativa ou fortalecimento leve.
                - Nunca use descanso, mobilidade, recuperacao ativa ou fortalecimento nos dias disponiveis informados.
                - Nunca programe corrida, prova ou competicao em dias nao selecionados como disponiveis.
                - Progrida de forma coerente e evite aumento brusco de volume.
                - Ajuste intensidade, volume e complexidade ao perfil, objetivo, ritmo, lesoes e observacoes.
                - Retorne apenas JSON valido, sem markdown.

                Regras de texto:
                - resumo: no maximo 3 frases.
                - foco: 1 frase.
                - descricao: 1 frase curta.
                - observacoes: 1 frase curta.
                - Nao repita distancia, pace ou duracao na descricao quando esses dados ja estiverem nos campos proprios.
                - Descanso e fortalecimento devem usar textos curtos e padronizados.

                Orientacao do ciclo:
                %s

                JSON obrigatorio:
                {
                  "titulo": "",
                  "resumo": "",
                  "duracaoSemanas": %d,
                  "objetivoPlano": "",
                  "semanas": [
                    {
                      "numeroSemana": 1,
                      "titulo": "",
                      "foco": "",
                      "treinos": [
                        {
                          "diaSemana": "segunda-feira",
                          "titulo": "",
                          "tipo": "",
                          "descricao": "",
                          "distanciaKm": "",
                          "duracaoEstimada": "",
                          "paceSugerido": "",
                          "observacoes": ""
                        }
                      ]
                    }
                  ]
                }

                Dados do atleta:
                - Objetivo: %s
                - Experiencia: %s
                - Volume semanal atual: %s
                - Ritmo confortavel atual: %s
                - Distancia alvo: %s
                - Dias disponiveis: %s
                - Possui prova marcada: %s
                - Data atual: %s
                - Data da prova: %s
                - Distancia da prova: %s
                - Objetivo da prova: %s
                - Tempo desejado: %s
                - Importancia da prova: %s
                - Possui lesao: %s
                - Observacoes: %s
                """.formatted(
                duracaoSemanas,
                orientacaoCiclo(request),
                duracaoSemanas,
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                request.getDistanciaAlvo(),
                request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Nao",
                LocalDate.now(),
                request.getDataProva() == null ? "Nao informado" : request.getDataProva(),
                valor(request.getDistanciaProva()),
                valor(request.getObjetivoProva()),
                valor(request.getTempoDesejado()),
                valor(request.getImportanciaProva()),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Nao",
                valor(request.getObservacoes())
        );
    }

    private String orientacaoCiclo(GerarPlanoTreinoRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiProva())) {
            return """
                    - Oriente o ciclo pela prova informada: data, distancia, meta e importancia.
                    - O ciclo com prova deve ter no minimo 4 e no maximo 6 semanas.
                    - Se a prova acontecer antes do fim do ciclo, prepare o atleta ate a prova e use as semanas restantes para recuperacao e retorno gradual.
                    - Se a prova estiver a mais de 6 semanas, gere apenas o primeiro ciclo orientado a ela.
                    """;
        }

        return """
                - Oriente o ciclo pelo objetivo geral do corredor.
                - Nao use taper de prova; priorize consistencia, base, resistencia, tecnica ou condicionamento conforme o objetivo.
                """;
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Nao informado";
    }
}

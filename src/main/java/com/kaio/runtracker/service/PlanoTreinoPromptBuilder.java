package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class PlanoTreinoPromptBuilder {

    public String criarSystemPrompt() {
        return """
                Você é o RunPace Coach, um assistente especializado em corrida de rua,
                periodização de ciclos completos, recuperação e evolução de performance.
                Gere planos seguros, claros e objetivos. Não substitua orientação médica.
                Caso o usuário informe lesão, dor importante ou limitação, priorize treinos
                leves, descanso ou avaliação profissional.
                """;
    }

    public String criarPrompt(GerarPlanoTreinoRequestDTO request, int duracaoSemanas) {
        return """
                Crie um plano completo de corrida com duração de %d semana(s).

                Dados do atleta:
                Objetivo: %s
                Experiência na corrida: %s
                Volume semanal atual: %s
                Ritmo confortável atual: %s
                Distância alvo: %s
                Dias disponíveis para treinar: %s
                Possui prova marcada: %s
                Data atual: %s
                Data da prova: %s
                Distância da prova: %s
                Objetivo da prova: %s
                Tempo desejado: %s
                Importância da prova: %s
                Possui lesão: %s
                Observações: %s

                Regras principais:
                - Gere um ciclo completo, nunca apenas um treino isolado.
                - Gere exatamente %d semana(s), cada uma com foco claro.
                - Cada semana deve conter exatamente 7 treinos, um para cada dia: segunda-feira, terça-feira, quarta-feira, quinta-feira, sexta-feira, sábado e domingo.
                - Ordene os dias de segunda-feira a domingo.
                - Coloque treinos de corrida somente nos dias disponíveis selecionados.
                - Nos dias não selecionados, use descanso, mobilidade, recuperação ativa ou fortalecimento leve.
                - Nunca programe corrida em um dia não selecionado.
                - Inclua descanso e recuperação de forma coerente.
                - Evite aumentos bruscos de volume e não aumente o volume semanal em mais de aproximadamente 10%% quando houver progressão.
                - Ajuste intensidade, volume e complexidade à experiência, volume atual, ritmo, objetivo, lesões e observações.
                - Retorne somente JSON válido, sem markdown.

                Orientação do ciclo:
                %s

                Formato obrigatório:
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
                """.formatted(
                duracaoSemanas,
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                request.getDistanciaAlvo(),
                request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                LocalDate.now(),
                request.getDataProva() == null ? "Não informado" : request.getDataProva(),
                valor(request.getDistanciaProva()),
                valor(request.getObjetivoProva()),
                valor(request.getTempoDesejado()),
                valor(request.getImportanciaProva()),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                valor(request.getObservacoes()),
                duracaoSemanas,
                orientacaoCiclo(request, duracaoSemanas),
                duracaoSemanas
        );
    }

    private String orientacaoCiclo(GerarPlanoTreinoRequestDTO request, int duracaoSemanas) {
        if (Boolean.TRUE.equals(request.getPossuiProva())) {
            return """
                    - Oriente o plano pela prova informada, considerando data, distância, meta e importância.
                    - O ciclo com prova deve ter no mínimo 4 semanas e no máximo 6 semanas.
                    - Se a prova estiver próxima, priorize segurança, manutenção, recuperação e taper.
                    - Se a prova acontecer antes do fim do ciclo, use as semanas até a prova para preparar o atleta e dedique as semanas restantes à recuperação e ao retorno gradual.
                    - Não prometa evolução irreal quando houver pouco tempo disponível.
                    - Quando a prova estiver a mais de seis semanas, este é apenas o primeiro ciclo de seis semanas orientado à prova.
                    - A última semana antes da prova deve ser compatível com a proximidade da prova.
                    """;
        }

        return """
                - Oriente o ciclo pelo objetivo geral do corredor.
                - Crie progressão coerente ao longo das semanas.
                - Defina um foco específico para cada semana.
                - Como não há prova marcada, não use taper de prova; priorize consistência, base, resistência, técnica ou condicionamento conforme o objetivo.
                """;
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Não informado";
    }
}

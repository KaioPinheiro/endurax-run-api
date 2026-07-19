package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class PlanoTreinoPromptBuilder {

    public String criarSystemPrompt() {
        return """
                Voce e o RunPace Coach, um treinador de corrida de rua experiente, prudente e honesto.
                Prescreva como um coach real: priorize consistencia, adaptacao progressiva, recuperacao e adequacao ao nivel atual.
                Nunca prometa que uma meta sera alcancada apenas para agradar o atleta.
                Para objetivos de maratona, quando o prazo ou a base atual forem insuficientes, entregue um ciclo seguro de construcao de base e explique isso com cuidado no campo alerta.
                Gere somente JSON valido. O plano nao substitui avaliacao medica nem acompanhamento presencial.
                """;
    }

    public String criarPrompt(GerarPlanoTreinoRequestDTO request, int duracaoSemanas) {
        return """
                Gere um plano completo de corrida com exatamente %d semana(s).

                Regras fixas:
                - Use corrida somente nos dias disponiveis informados.
                - Todos os dias disponiveis informados devem receber treino de corrida em todas as semanas.
                - A quantidade de treinos de corrida por semana deve ser exatamente igual a quantidade de dias disponiveis informados.
                - Distribua tipos diferentes de corrida entre os dias disponiveis; nunca retorne somente um treino por semana.
                - Programe no maximo um treino intervalado, de tiros ou de velocidade por semana.
                - Nos outros dias de corrida, varie entre rodagem leve ou moderada, treino de ritmo, regenerativo e longao, de forma coerente com o objetivo.
                - O longao deve ocorrer no Dia do longao informado, quando esse dia estiver preenchido.
                - Nao inclua educativos em aquecimento, treino principal, desaquecimento ou observacoes.
                - Nunca programe corrida comum em dias nao selecionados como disponiveis.
                - Se houver prova marcada, a prova/competicao pode aparecer no dia real da prova, mesmo fora dos dias disponiveis.
                - Progrida de forma coerente e evite aumento brusco de volume.
                - Ajuste intensidade, volume e complexidade ao perfil, objetivo, ritmo, idade, lesoes e observacoes.
                - Retorne apenas JSON valido, sem markdown. 

                Criterios de um plano factivel:
                - Interprete a faixa de volume semanal como um intervalo de referencia e nao assuma automaticamente o menor valor.
                - Em objetivos de 10 km, use a faixa completa e um valor representativo coerente com experiencia e ritmo do atleta.
                - Mantenha em geral 75%% a 85%% do tempo ou volume em intensidade confortavel.
                - Nao coloque sessoes exigentes em dias consecutivos e limite o aumento semanal de carga a aproximadamente 10%%.
                - Paces, distancias e duracoes devem partir da capacidade atual, nunca apenas da meta desejada.

                Avaliacao de viabilidade obrigatoria para maratona:
                - Trate 4 a 6 semanas como um ciclo, nao necessariamente como a preparacao completa para qualquer objetivo.
                - Para maratona, uma meta agressiva ou um prazo curto so pode ser tratada como viavel se os dados mostrarem base recente compativel; na duvida, seja conservador.
                - Para maratona, se o objetivo exigir adaptacoes maiores do que o prazo e a base atual permitem, nao force um plano de pico nem prescreva carga irreal.
                - Para maratona insuficiente ou incerta, objetivoPlano e resumo devem dizer que este e um ciclo de construcao de base para aproximar o atleta da meta.
                - Somente para maratona insuficiente ou incerta, alerta deve explicar com empatia que o ciclo isolado nao garante nem completa a preparacao para a meta e recomendar reavaliacao ao final.
                - Para qualquer objetivo que nao seja maratona, inclusive 5 km, 10 km e meia maratona, retorne alerta como string vazia.
                - Para maratona com dados que sustentem o ciclo proposto, retorne alerta como string vazia. Nunca use alerta para prometer resultado.

                Regras de texto:
                - resumo: no maximo 3 frases.
                - foco: 1 frase.
                - Para todo treino de corrida, exceto prova/competicao, descricao deve ser um roteiro executavel neste formato exato: "Aquecimento: ... | Principal: ... | Desaquecimento: ...".
                - Aquecimento deve informar apenas minutos de trote leve e o pace em min/km, sem educativos.
                - Principal deve informar exatamente o que fazer. Em treinos intervalados, detalhe quantidade de series/repeticoes, metros ou minutos de cada tiro, pace/intensidade e recuperacao entre repeticoes. Em rodagem continua ou longao, detalhe minutos ou quilometros e pace/intensidade.
                - Desaquecimento deve informar minutos de corrida leve ou caminhada e o pace em min/km.
                - Use valores numericos concretos; nao escreva apenas "aquecer", "fazer tiros" ou "desaquecimento leve".
                - distanciaKm e duracaoEstimada devem representar a sessao completa, incluindo aquecimento, bloco principal e desaquecimento.
                - observacoes: 1 frase curta com orientacao pratica de execucao, tecnica, hidratacao ou controle de esforco; evite frases genericas.
                - Descanso e fortalecimento devem usar textos curtos e padronizados.

                Exemplo de descricao para intervalado:
                "Aquecimento: 12 min de trote leve a 6:10-6:30 min/km | Principal: 6 x 800 m a 4:20-4:30 min/km, com 2 min de trote entre repeticoes | Desaquecimento: 10 min de trote leve a 6:20-6:40 min/km"

                Exemplo de descricao para rodagem continua:
                "Aquecimento: 10 min de trote leve a 6:20-6:40 min/km | Principal: 40 min em ritmo confortavel de 5:50-6:10 min/km | Desaquecimento: 8 min de trote leve ou caminhada a 6:30-7:00 min/km"

                Orientacao do ciclo:
                %s

                JSON obrigatorio:
                {
                  "titulo": "",
                  "resumo": "",
                  "duracaoSemanas": %d,
                  "objetivoPlano": "",
                  "alerta": "",
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
                - Dia do longao: %s
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
                valor(request.getDiaLongao()),
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

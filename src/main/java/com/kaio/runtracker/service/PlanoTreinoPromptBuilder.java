package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoTreinoRequestDTO;
import com.kaio.runtracker.ai.prompt.PromptObjetivoFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class PlanoTreinoPromptBuilder {

    private final PromptObjetivoFactory promptObjetivoFactory;

    public PlanoTreinoPromptBuilder(
            PromptObjetivoFactory promptObjetivoFactory) {
        this.promptObjetivoFactory = promptObjetivoFactory;
    }

    public String criarSystemPrompt() {
        return """
                Voce e o RunPace Coach, um treinador de corrida de rua experiente, prudente e honesto.
                Prescreva como um coach real: priorize consistencia, adaptacao progressiva, recuperacao e adequacao ao nivel atual.
                Nunca prometa que uma meta sera alcancada apenas para agradar o atleta.
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
                - Programe no maximo dois treinos intensos por semana e nunca em dias consecutivos.
                - Nos outros dias de corrida, varie entre rodagem leve ou moderada, treino de ritmo, regenerativo e longao, de forma coerente com o objetivo.
                - Toda semana deve incluir ao menos um treino leve ou regenerativo.
                - O longao deve ocorrer no Dia do longao informado, quando esse dia estiver preenchido.
                - Nao inclua educativos em aquecimento, treino principal, desaquecimento ou observacoes.
                - Nunca programe corrida comum em dias nao selecionados como disponiveis.
                - Se houver prova marcada, a prova/competicao pode aparecer no dia real da prova, mesmo fora dos dias disponiveis.
                - Progrida de forma coerente e evite aumento brusco de volume.
                - Nao repita semanas identicas sem justificativa.
                - Reduza a carga antes da prova.
                - Quando a prova estiver dentro das quatro semanas do plano, use o período posterior somente para recuperação e retorno progressivo, sem tratá-lo como preparação para uma prova futura.
                - Nao invente informacoes ausentes.
                - Ajuste intensidade, volume e complexidade ao perfil, objetivo, ritmo, idade, lesoes e observacoes.
                - Defina internamente o nivel real do corredor principalmente pela resposta sobre correr 5 km sem caminhar e, quando a resposta for positiva, pelo tempo atual nos 5 km.
                - Quem ainda nao corre 5 km direto deve ser tratado como iniciante. Para quem corre, use o tempo dos 5 km para estimar de forma prudente se o nivel e iniciante, intermediario ou avancado e calibrar volume, pace, intensidade e complexidade.
                - A experiencia declarada e apenas contexto complementar e nao deve se sobrepor a capacidade demonstrada nos 5 km.
                - Retorne apenas JSON valido, sem markdown. 

                Criterios de um plano factivel:
                - Interprete a faixa de volume semanal como um intervalo de referencia e nao assuma automaticamente o menor valor.
                %s
                - Mantenha em geral 75%% a 85%% do tempo ou volume em intensidade confortavel.
                - Nao coloque sessoes exigentes em dias consecutivos e limite o aumento semanal de carga a aproximadamente 10%%.
                - Paces, distancias e duracoes devem partir da capacidade atual, nunca apenas da meta desejada.
                - Se o atleta nunca correu, comece com sessoes alternando caminhada e blocos curtos de trote ou corrida leve, sempre em intensidade confortavel e controlada.
                - Progrida primeiro o tempo dos blocos de trote, depois reduza gradualmente as pausas caminhando e, somente quando houver adaptacao segura, introduza trechos curtos de corrida continua devagar.
                - Trechos um pouco mais rapidos so podem aparecer de forma curta, controlada e progressiva depois de demonstrada adaptacao, se idade, lesoes, observacoes e recuperacao permitirem; nunca use esforco maximo nem priorize velocidade sobre seguranca.
                - Para quem ainda nao corre 5 km direto, prescreva principalmente por tempo e percepcao de esforco, sem tiros, intervalados intensos, treino de ritmo forte ou pace obrigatorio.
                - Para quem ainda nao corre 5 km direto, use tipo "Leve" nas sessoes comuns e "Longao" somente quando ele for exigido pelo objetivo; use titulo claro como "Corrida e caminhada" enquanto houver alternancia e detalhe no bloco Principal os minutos de trote, corrida e caminhada e a quantidade de repeticoes.
                - Ao definir qualquer progressao para iniciantes, avalie explicitamente idade e presenca de lesao. Em caso de risco, dor ou duvida, reduza impacto, intensidade e duracao e priorize caminhada, trote leve e recuperacao.
                - Quando a Experiencia for "Nunca corri", todas as sessoes devem usar caminhada no Aquecimento e caminhada no Desaquecimento; nunca use trote ou corrida nesses dois blocos.
                - Quando a Experiencia for "Nunca corri", o Principal deve ser dividido em passos distintos e executaveis de trote leve e caminhada, informando a duracao de cada passo e a quantidade de repeticoes.
                - Nesse caso, escreva o Principal em formato estruturado, por exemplo: "3 x (2 min de trote leve + 3 min de caminhada)". Nao agrupe trote e caminhada em um unico texto sem separar os passos.
                - Aumente gradualmente os minutos de trote leve ao longo das semanas e reduza a caminhada somente quando isso for seguro. Nao repita a mesma proporcao de trote e caminhada em todas as semanas.
                - Use idade, lesao e observacoes para decidir a progressao: atletas mais velhos, lesionados, com dor ou com fatores de risco devem ter blocos de trote menores, mais caminhada e progressao mais lenta.
                - Somente para atleta sem lesao relevante, sem relato de dor e com idade e adaptacao compativeis, as semanas finais podem conter blocos mais longos de trote ou corrida leve continua; mantenha intensidade confortavel e inclua pausas caminhando quando necessario.
                - Se o atleta corre 5 km direto, use o tempo informado somente como referencia da capacidade atual, sem transformar esse resultado em promessa de desempenho.
                - Para meia maratona e maratona, considere a maior distancia ja percorrida como limite da base atual; nao prescreva salto brusco de longao a partir dela.

                Regras de texto:
                - resumo: no maximo 3 frases.
                - foco: 1 frase.
                - Para todo treino de corrida, exceto prova/competicao, descricao deve ser um roteiro executavel neste formato exato: "Aquecimento: ... | Principal: ... | Desaquecimento: ...".
                - Para Experiencia "Nunca corri", Aquecimento deve informar minutos de caminhada e o pace em min/km. Para os demais corredores, deve informar minutos de trote leve e o pace em min/km. Nunca inclua educativos.
                - Principal deve informar exatamente o que fazer. Em treinos intervalados, detalhe quantidade de series/repeticoes, metros ou minutos de cada tiro, pace/intensidade e recuperacao entre repeticoes. Em rodagem continua ou longao, detalhe minutos ou quilometros e pace/intensidade.
                - Para Experiencia "Nunca corri", Desaquecimento deve ser sempre caminhada com minutos e pace em min/km. Para os demais corredores, pode usar corrida leve ou caminhada.
                - Use valores numericos concretos; nao escreva apenas "aquecer", "fazer tiros" ou "desaquecimento leve".
                - distanciaKm e duracaoEstimada devem representar a sessao completa, incluindo aquecimento, bloco principal e desaquecimento.
                - distanciaKm e paceSugerido sao obrigatorios em todo treino de corrida. distanciaKm deve ser numerica e maior que zero; paceSugerido deve trazer uma faixa em min/km ou "Por percepcao de esforco" para iniciantes sem pace seguro.
                - Calcule duracaoEstimada pela soma exata dos blocos. Em repeticoes, multiplique a soma dos passos pela quantidade de repeticoes e depois adicione aquecimento e desaquecimento.
                - Exemplo: 5 min de aquecimento + 3 x (10 min de caminhada + 1 min de trote + 2 min de caminhada) + 5 min de desaquecimento totaliza 49 min, nunca 30 min.
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
                - Distancia da meta de performance: %s
                - Tempo atual na distancia alvo: %s
                - Tempo desejado na distancia alvo: %s
                - Corre 5 km direto sem caminhar: %s
                - Tempo atual nos 5 km: %s
                - Maior distancia ja percorrida: %s
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
                promptObjetivoFactory.criarPrompt(request).strip(),
                orientacaoCiclo(request),
                duracaoSemanas,
                request.getObjetivo(),
                distanciaPerformance(request),
                valor(request.getTempoAtual()),
                request.ehObjetivoPerformance() ? valor(request.getTempoDesejado()) : "Nao se aplica",
                respostaSimNao(request.getCorre5KmSemCaminhar()),
                valor(request.getTempo5Km()),
                valor(request.getMaiorDistanciaCorrida()),
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

    public String orientacaoCiclo(GerarPlanoTreinoRequestDTO request) {
        if (Boolean.TRUE.equals(request.getPossuiProva())) {
            if (provaProxima(request)) {
                return """
                        - A prova está a menos de quatro semanas, mas o plano deve manter exatamente quatro semanas.
                        - Posicione a prova na semana e no dia correspondentes à data informada.
                        - Use o período anterior para preparação curta, segura e realista, sem prometer preparação completa ou alcance da meta.
                        - Use o período posterior para recuperação adaptada à distância da prova e retorno progressivo.
                        - Não programe treino intenso nos sete dias posteriores à prova.
                        - Não trate os treinos posteriores como preparação para uma prova que já ocorreu.
                        - Preencha alerta com uma explicação clara de que o prazo é insuficiente para um ciclo completo e de que o plano continua com recuperação e evolução após o evento.
                        - Modelo de alerta: "Sua prova está muito próxima para a realização de um ciclo completo de preparação. O plano foi estruturado com quatro semanas, utilizando o período anterior à prova para uma preparação segura e as semanas seguintes para recuperação e continuidade dos treinos."
                        """;
            }
            return """
                    - Oriente o ciclo pela prova informada: data, distancia, meta e importancia.
                    - O ciclo com prova deve ter no minimo 4 e no maximo 6 semanas.
                    - Nao programe nenhum treino depois da data da prova.
                    - Se a prova estiver a mais de 6 semanas, gere apenas o primeiro ciclo orientado a ela.
                    """;
        }

        return """
                - Oriente o ciclo pelo objetivo geral do corredor.
                - Nao use taper de prova; priorize consistencia, base, resistencia, tecnica ou condicionamento conforme o objetivo.
                """;
    }

    private boolean provaProxima(GerarPlanoTreinoRequestDTO request) {
        if (request.getDataProva() == null) {
            return false;
        }
        long dias = ChronoUnit.DAYS.between(LocalDate.now(), request.getDataProva());
        return dias >= 0 && dias < 28;
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Nao informado";
    }

    private String distanciaPerformance(GerarPlanoTreinoRequestDTO request) {
        if (!request.ehObjetivoPerformance()) return "Nao se aplica";
        String objetivo = request.getObjetivo();
        if (objetivo.contains("5 km")) return "5 km";
        if (objetivo.contains("10 km")) return "10 km";
        if (objetivo.contains("Meia Maratona")) return "21 km (Meia Maratona)";
        return "42 km (Maratona)";
    }

    private String respostaSimNao(Boolean valor) {
        if (valor == null) {
            return "Nao informado";
        }
        return valor ? "Sim" : "Nao";
    }
}

package com.kaio.runtracker.service;

import com.kaio.runtracker.dto.GerarPlanoSemanalRequestDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

@Service
public class PlanoSemanalPromptBuilder {

    public String criarSystemPrompt() {
        return """
                Você é o RunPace Coach, um assistente especializado em corrida de rua, \
                periodização semanal, recuperação e evolução de performance. Gere planos \
                seguros, claros e objetivos. Não substitua orientação médica. Caso o usuário \
                informe lesão, dor importante ou limitação, priorize treinos leves, descanso \
                ou avaliação profissional.
                """;
    }

    public String criarPrompt(GerarPlanoSemanalRequestDTO request) {
        return """
                Crie um plano semanal de corrida com base nos dados abaixo:

                Objetivo: %s
                Experiência na corrida: %s
                Volume semanal atual: %s
                Ritmo confortável atual: %s
                Data atual: %s
                Possui prova marcada: %s
                Data da prova: %s
                Distância da prova: %s
                Outra distância: %s
                Objetivo da prova: %s
                Tempo desejado: %s
                Importância da prova: %s
                Distância alvo: %s
                Dias disponíveis para treinar:
                %s
                Possui lesão: %s
                Descrição da lesão: %s
                Intensidade desejada: %s
                Observações: %s

                Antes de montar o treino, analise automaticamente o perfil do atleta e classifique-o como iniciante, intermediário ou avançado utilizando as informações fornecidas.

                Considere principalmente:
                - Experiência na corrida
                - Volume semanal atual
                - Ritmo confortável
                - Distância alvo
                - Objetivo
                - Prova marcada (quando houver)
                - Lesões ou limitações
                - Observações adicionais

                Não utilize autopercepção do atleta para definir essa classificação.

                Utilize essa classificação apenas internamente para definir intensidade, volume, progressão e complexidade do treinamento.
                A classificação não deve aparecer para o usuário.

                Regras do plano:
                - Todos os títulos e tipos devem estar em português correto.
                - Não utilize abreviações nos títulos ou tipos.
                - Não cometa erros ortográficos como "Corida", "Corrrida", "Corria", "Interbalado", "Fortalescimento", "Mobilidadee", "Resistencia" ou "Velocidadee".
                - Para o campo "tipo" e para títulos que representem categorias conhecidas, utilize exatamente uma das nomenclaturas padronizadas do RunPace: "Corrida contínua", "Corrida longa", "Treino de velocidade", "Treino de resistência", "Intervalado", "Fartlek", "Recuperação ativa", "Mobilidade", "Fortalecimento", "Descanso" ou "Regenerativo".
                - O array "treinos" deve conter obrigatoriamente exatamente 7 itens.
                - Deve existir exatamente um item para cada dia: segunda-feira, terça-feira, quarta-feira, quinta-feira, sexta-feira, sábado e domingo.
                - Nunca omita dias, mesmo quando não houver corrida programada.
                - Em dias sem corrida, retorne um item com tipo "Descanso", "Mobilidade", "Recuperação ativa" ou "Fortalecimento leve".
                - Ordene os itens de segunda-feira a domingo.
                - Gere exatamente 7 dias, de segunda-feira a domingo.
                - Coloque treinos de corrida somente nos dias disponíveis selecionados.
                - Nos dias não selecionados, use descanso, mobilidade, recuperação ativa ou fortalecimento leve.
                - Nunca programe corrida em um dia não selecionado.
                - Defina automaticamente duração, distância e intensidade de cada sessão conforme objetivo, experiência, volume, ritmo, distância alvo, dias disponíveis, prova, lesões e observações.
                - Não gere mais dias fortes do que o adequado ao perfil inferido.
                - Ajuste intensidade, volume e complexidade à experiência na corrida informada.
                - Para "Nunca corri", priorize adaptação leve, progressiva e introdutória.
                - Para "Ainda não corro", crie um plano introdutório e progressivo.
                - Para "Não sei informar", adote uma prescrição conservadora.
                - Quanto maior o volume semanal atual, mais estruturado pode ser o plano, respeitando objetivo e lesões.
                - Para ritmo "Ainda não sei informar", prescreva por tempo e percepção de esforço, sem exigir pace.
                - Para "Caminhada / trote leve", priorize adaptação progressiva.
                - Quando houver faixa de pace, use-a como referência para os paces sugeridos.
                - Se houver fadiga ou lesão, reduza a intensidade.
                - Se não houver prova marcada ou a data já tiver passado, ignore a prova como objetivo principal.
                - Se faltarem mais de 90 dias, priorize base aeróbica, evolução gradual de volume, resistência e fortalecimento.
                - Se faltarem entre 31 e 90 dias, inicie a fase específica com intervalados, tempo run e longões progressivos adequados ao atleta.
                - Se faltarem entre 8 e 30 dias, refine a preparação, mantenha intensidade, controle volume e evite aumentos bruscos.
                - Se faltarem entre 1 e 7 dias, aplique taper, reduza significativamente o volume e priorize estímulos leves e recuperação.
                - Para "Prova principal da temporada", organize base, desenvolvimento, especificidade e taper visando pico de performance.
                - Para "Prova importante", priorize a prova sem comprometer totalmente a evolução de longo prazo.
                - Para "Apenas participar", trate a prova como parte do processo, sem periodização agressiva.
                - Nunca aumente o volume semanal em mais de aproximadamente 10%% em relação ao volume informado.
                - Distribua recuperação entre estímulos intensos e não programe treinos fortes em dias consecutivos.
                - Inclua ao menos um dia semanal de descanso completo ou recuperação ativa.
                - Quando houver prova futura, faça todas as decisões ajudarem o atleta a chegar melhor preparado.
                - Retorne somente JSON válido, sem markdown.

                Formato obrigatório:
                {
                  "tituloPlano": "",
                  "objetivo": "",
                  "observacoesGerais": "",
                  "alerta": "",
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
                    },
                    {
                      "diaSemana": "terça-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "quarta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "quinta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "sexta-feira",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "sábado",
                      "titulo": "",
                      "tipo": "",
                      "descricao": "",
                      "distanciaKm": "",
                      "duracaoEstimada": "",
                      "paceSugerido": "",
                      "observacoes": ""
                    },
                    {
                      "diaSemana": "domingo",
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
                """.formatted(
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                LocalDate.now(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "Não",
                request.getDataProva() == null ? "Não informado" : request.getDataProva(),
                valor(request.getDistanciaProva()),
                valor(request.getOutraDistanciaProva()),
                valor(request.getObjetivoProva()),
                valor(request.getTempoDesejadoProva()),
                valor(request.getImportanciaProva()),
                request.getDistanciaAlvo(), request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "Não",
                valor(request.getDescricaoLesao()),
                request.getIntensidadeDesejada(),
                valor(request.getObservacoes())
        );
    }

    private String valor(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "Não informado";
    }
}

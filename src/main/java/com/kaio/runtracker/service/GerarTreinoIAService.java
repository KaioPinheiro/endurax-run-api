package com.kaio.runtracker.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kaio.runtracker.dto.GerarTreinoRequestDTO;
import com.kaio.runtracker.dto.GerarTreinoResponseDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;

import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@Service
public class GerarTreinoIAService {

    private static final String SYSTEM_PROMPT = """
            VocÃª Ã© o RunPace Coach, um assistente especializado em corrida de rua, \
            prescriÃ§Ã£o de treinos, recuperaÃ§Ã£o e evoluÃ§Ã£o de performance. Gere treinos \
            seguros, claros e objetivos. NÃ£o substitua orientaÃ§Ã£o mÃ©dica. Caso o usuÃ¡rio \
            informe lesÃ£o, dor importante ou limitaÃ§Ã£o, recomende treino leve, descanso \
            ou avaliaÃ§Ã£o profissional.
            """;

    private final OpenAIService openAIService;
    private final ObjectMapper objectMapper;

    public GerarTreinoIAService(
            OpenAIService openAIService,
            ObjectMapper objectMapper) {
        this.openAIService = openAIService;
        this.objectMapper = objectMapper;
    }

    public GerarTreinoResponseDTO gerarTreino(GerarTreinoRequestDTO request) {
        try {
            String conteudo = openAIService.enviarPromptTreino(
                    SYSTEM_PROMPT,
                    criarUserPrompt(request));

            GerarTreinoResponseDTO treino =
                    objectMapper.readValue(conteudo, GerarTreinoResponseDTO.class);

            if (!StringUtils.hasText(treino.getTitulo())
                    || !StringUtils.hasText(treino.getTipo())
                    || !StringUtils.hasText(treino.getDescricao())) {
                throw new GerarTreinoIAException(
                        BAD_GATEWAY,
                        "O serviço retornou um treino incompleto. Tente gerar novamente."
                );
            }

            return treino;
        } catch (GerarTreinoIAException exception) {
            throw exception;
        } catch (JsonProcessingException exception) {
            throw new GerarTreinoIAException(
                    BAD_GATEWAY,
                    "O serviço retornou um formato de treino inválido. Tente novamente.",
                    exception
            );
        }
    }

    private String criarUserPrompt(GerarTreinoRequestDTO request) {
        return """
                Crie um treino de corrida personalizado com base nos dados abaixo:

                Objetivo: %s
                ExperiÃªncia na corrida: %s
                Volume semanal atual: %s
                Ritmo confortÃ¡vel atual: %s
                Data atual: %s
                Possui prova marcada: %s
                Data da prova: %s
                DistÃ¢ncia da prova: %s
                Outra distÃ¢ncia: %s
                Objetivo da prova: %s
                Tempo desejado: %s
                ImportÃ¢ncia da prova: %s
                DistÃ¢ncia alvo: %s
                Dias disponÃ­veis para treinar:
                %s
                Possui lesÃ£o: %s
                DescriÃ§Ã£o da lesÃ£o: %s
                Intensidade desejada: %s
                ObservaÃ§Ãµes: %s

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

                Regras de personalização:
                - Ajuste intensidade, volume e complexidade Ã  experiÃªncia na corrida informada.
                - Para "Nunca corri", priorize adaptaÃ§Ã£o leve, progressiva e introdutÃ³ria.
                - Para "Ainda nÃ£o corro", crie um treino introdutÃ³rio e progressivo.
                - Para "NÃ£o sei informar", adote uma prescriÃ§Ã£o conservadora.
                - Quanto maior o volume semanal atual, mais estruturado pode ser o treino, respeitando objetivo e lesões.
                - Para ritmo "Ainda nÃ£o sei informar", prescreva por tempo e percepÃ§Ã£o de esforÃ§o, sem exigir pace.
                - Para "Caminhada / trote leve", priorize adaptaÃ§Ã£o progressiva.
                - Quando houver faixa de pace, use-a como referÃªncia para o pace sugerido.
                - Para experiÃªncias maiores, estruture o treino conforme objetivo, perfil inferido e segurança.
                - Gere o treino preferencialmente em um dos dias disponÃ­veis informados.
                - Defina automaticamente duraÃ§Ã£o, distÃ¢ncia e intensidade coerentes com objetivo, experiência, volume, ritmo, distÃ¢ncia alvo, dias disponÃ­veis, prova, lesÃµes e observaÃ§Ãµes.

                Regras de prova e periodizaÃ§Ã£o:
                - Se nÃ£o houver prova marcada ou a data jÃ¡ tiver passado, ignore a prova como objetivo principal.
                - Se faltarem mais de 90 dias, priorize base aerÃ³bica, evoluÃ§Ã£o gradual de volume, resistÃªncia e fortalecimento.
                - Se faltarem entre 31 e 90 dias, inicie a fase especÃ­fica com intervalados, tempo run e longÃµes progressivos adequados ao atleta.
                - Se faltarem entre 8 e 30 dias, refine a preparaÃ§Ã£o, mantenha intensidade, controle volume e evite aumentos bruscos.
                - Se faltarem entre 1 e 7 dias, aplique taper, reduza significativamente o volume e priorize estÃ­mulos leves e recuperaÃ§Ã£o.
                - Para "Prova principal da temporada", organize base, desenvolvimento, especificidade e taper visando pico de performance.
                - Para "Prova importante", priorize a prova sem comprometer totalmente a evoluÃ§Ã£o de longo prazo.
                - Para "Apenas participar", trate a prova como parte do processo, sem periodizaÃ§Ã£o agressiva.
                - Nunca aumente o volume semanal em mais de aproximadamente 10%% em relaÃ§Ã£o ao volume informado.
                - Priorize seguranÃ§a diante de lesÃ£o, fadiga ou limitaÃ§Ã£o.
                - Distribua recuperaÃ§Ã£o entre estÃ­mulos intensos e nÃ£o programe treinos fortes em dias consecutivos.
                - Inclua ao menos um dia semanal de descanso completo ou recuperaÃ§Ã£o ativa.
                - Quando houver prova futura, faÃ§a as decisÃµes ajudarem o atleta a chegar melhor preparado.

                Retorne um treino em JSON vÃ¡lido, sem markdown, no seguinte formato:

                {
                  "titulo": "",
                  "tipo": "",
                  "descricao": "",
                  "distanciaKm": "",
                  "duracaoEstimada": "",
                  "paceSugerido": "",
                  "observacoes": "",
                  "alerta": ""
                }

                Preencha todos os campos como texto. Quando nÃ£o houver alerta especÃ­fico, \
                use uma recomendaÃ§Ã£o geral de seguranÃ§a no campo "alerta".
                """.formatted(
                request.getObjetivo(),
                request.getExperienciaCorrida(),
                request.getVolumeSemanalAtual(),
                request.getRitmoConfortavel(),
                LocalDate.now(),
                Boolean.TRUE.equals(request.getPossuiProva()) ? "Sim" : "NÃ£o",
                request.getDataProva() == null ? "NÃ£o informado" : request.getDataProva(),
                valorOuNaoInformado(request.getDistanciaProva()),
                valorOuNaoInformado(request.getOutraDistanciaProva()),
                valorOuNaoInformado(request.getObjetivoProva()),
                valorOuNaoInformado(request.getTempoDesejadoProva()),
                valorOuNaoInformado(request.getImportanciaProva()),
                request.getDistanciaAlvo(),
                request.getDiasDisponiveis(),
                Boolean.TRUE.equals(request.getPossuiLesao()) ? "Sim" : "NÃ£o",
                valorOuNaoInformado(request.getDescricaoLesao()),
                request.getIntensidadeDesejada(),
                valorOuNaoInformado(request.getObservacoes())
        );
    }

    private String valorOuNaoInformado(String valor) {
        return StringUtils.hasText(valor) ? valor.trim() : "NÃ£o informado";
    }
}



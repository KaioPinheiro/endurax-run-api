package com.kaio.runtracker.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class OpenAIConfigurationLogger {

    private static final Logger logger =
            LoggerFactory.getLogger(OpenAIConfigurationLogger.class);

    private static final int TAMANHO_MINIMO_PARA_MASCARA = 12;

    public OpenAIConfigurationLogger(
            @Value("${openai.api.key:}") String apiKey) {
        boolean preenchida = StringUtils.hasText(apiKey);
        String chave = preenchida ? apiKey.trim() : "";
        boolean tamanhoSeguro = chave.length() >= TAMANHO_MINIMO_PARA_MASCARA;

        String primeirosSete = tamanhoSeguro
                ? chave.substring(0, 7)
                : "<indisponível>";
        String ultimosQuatro = tamanhoSeguro
                ? chave.substring(chave.length() - 4)
                : "<indisponível>";

        logger.info(
                "OpenAI API key: preenchida={}, primeiros7={}, ultimos4={}, tamanho={}",
                preenchida,
                primeirosSete,
                ultimosQuatro,
                chave.length()
        );

        logger.info(
                "Variável de ambiente OPENAI_API_KEY presente no processo={}",
                StringUtils.hasText(System.getenv("OPENAI_API_KEY"))
        );
    }
}

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

    public OpenAIConfigurationLogger(
            @Value("${openai.api.key:}") String apiKey) {
        boolean preenchida = StringUtils.hasText(apiKey);
        String chave = preenchida ? apiKey.trim() : "";

        logger.info(
                "OpenAI API key: preenchida={}, tamanho={}",
                preenchida,
                chave.length()
        );

        logger.info(
                "Variavel de ambiente OPENAI_API_KEY presente no processo={}",
                StringUtils.hasText(System.getenv("OPENAI_API_KEY"))
        );
    }
}

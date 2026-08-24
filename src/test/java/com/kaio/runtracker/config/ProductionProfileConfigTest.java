package com.kaio.runtracker.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ProductionProfileConfigTest {

    @Test
    void configuracaoBaseNaoForcaProfileDevEContainerAtivaProd() throws IOException {
        String applicationProperties = Files.readString(
                Path.of("src/main/resources/application.properties"));
        String dockerfile = Files.readString(Path.of("Dockerfile"));

        assertFalse(applicationProperties.contains("spring.profiles.active=dev"));
        assertTrue(applicationProperties.contains("spring.profiles.default=dev"));
        assertTrue(dockerfile.contains("--spring.profiles.active=prod"));
    }
}

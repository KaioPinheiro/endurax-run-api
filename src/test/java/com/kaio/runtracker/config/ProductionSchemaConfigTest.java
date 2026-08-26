package com.kaio.runtracker.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatNoException;

class ProductionSchemaConfigTest {

    @Test
    void productionUsesValidateAndKeepsFlywayEnabled() throws IOException {
        Properties prod = properties("src/main/resources/application-prod.properties");

        assertThat(prod.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        assertThat(prod.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(prod.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("false");
    }

    @Test
    void developmentKeepsCurrentUpdateBehavior() throws IOException {
        Properties dev = properties("src/main/resources/application-dev.properties");

        assertThat(dev.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("update");
        assertThat(dev.getProperty("spring.flyway.enabled")).isEqualTo("true");
        assertThat(dev.getProperty("spring.flyway.baseline-on-migrate")).isEqualTo("true");
    }

    @Test
    void baseConfigurationDoesNotForceUnsafeDdlBehavior() throws IOException {
        Properties base = properties("src/main/resources/application.properties");

        assertThat(base).doesNotContainKeys(
                "spring.jpa.hibernate.ddl-auto", "spring.flyway.enabled");
    }

    @Test
    void productionAcceptsOnlyValidateWithFlywayEnabled() {
        assertThatNoException().isThrownBy(
                () -> validator("prod", "validate", "true", "false").validate());

        assertThatIllegalStateException()
                .isThrownBy(() -> validator("prod", "update", "true", "false").validate())
                .withMessageContaining("ddl-auto=validate");
        assertThatIllegalStateException()
                .isThrownBy(() -> validator("prod", "validate", "false", "false").validate())
                .withMessageContaining("spring.flyway.enabled=true");
        assertThatIllegalStateException()
                .isThrownBy(() -> validator("prod", "validate", "true", "true").validate())
                .withMessageContaining("baseline-on-migrate=false");
    }

    @Test
    void developmentDoesNotApplyProductionRestriction() {
        assertThatNoException().isThrownBy(
                () -> validator("dev", "update", "true", "true").validate());
    }

    private ProductionSchemaConfigValidator validator(
            String profile, String ddlAuto, String flywayEnabled, String baselineOnMigrate) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(profile);
        environment.setProperty("spring.jpa.hibernate.ddl-auto", ddlAuto);
        environment.setProperty("spring.flyway.enabled", flywayEnabled);
        environment.setProperty("spring.flyway.baseline-on-migrate", baselineOnMigrate);
        return new ProductionSchemaConfigValidator(environment);
    }

    private Properties properties(String path) throws IOException {
        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(Path.of(path))) {
            properties.load(input);
        }
        return properties;
    }
}

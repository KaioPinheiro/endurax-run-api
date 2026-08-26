package com.kaio.runtracker.config;

import jakarta.annotation.PostConstruct;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

@Component
public class ProductionSchemaConfigValidator {

    private final Environment environment;

    public ProductionSchemaConfigValidator(Environment environment) {
        this.environment = environment;
    }

    @PostConstruct
    public void validate() {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        String ddlAuto = environment.getProperty("spring.jpa.hibernate.ddl-auto");
        if (!"validate".equalsIgnoreCase(ddlAuto)) {
            throw new IllegalStateException(
                    "Configuração incompatível: profile prod exige spring.jpa.hibernate.ddl-auto=validate.");
        }

        Boolean flywayEnabled = environment.getProperty("spring.flyway.enabled", Boolean.class);
        if (!Boolean.TRUE.equals(flywayEnabled)) {
            throw new IllegalStateException(
                    "Configuração incompatível: profile prod exige spring.flyway.enabled=true.");
        }

        Boolean baselineOnMigrate = environment.getProperty(
                "spring.flyway.baseline-on-migrate", Boolean.class);
        if (!Boolean.FALSE.equals(baselineOnMigrate)) {
            throw new IllegalStateException(
                    "Configuração incompatível: profile prod exige spring.flyway.baseline-on-migrate=false.");
        }
    }
}

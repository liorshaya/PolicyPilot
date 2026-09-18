package com.liorshaya.policypilot.config;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;

/**
 * Turns a platform-style {@code DATABASE_URL} into the {@code spring.datasource.*} properties.
 *
 * <p>Registered in {@code META-INF/spring.factories}. The derived property source sits right below the system
 * environment, so an explicit {@code SPRING_DATASOURCE_URL} still wins, and above every configuration file.
 * Nothing happens when {@code DATABASE_URL} is absent or already a JDBC URL.
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String VARIABLE = "DATABASE_URL";
    static final String PROPERTY_SOURCE_NAME = "policypilotDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String raw = environment.getProperty(VARIABLE);
        DatabaseUrl.parse(raw).ifPresent(url -> {
            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("spring.datasource.url", url.jdbcUrl());
            if (url.username() != null) {
                properties.put("spring.datasource.username", url.username());
            }
            if (url.password() != null) {
                properties.put("spring.datasource.password", url.password());
            }
            MapPropertySource source = new MapPropertySource(PROPERTY_SOURCE_NAME, properties);
            MutablePropertySources sources = environment.getPropertySources();
            if (sources.contains(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME)) {
                sources.addAfter(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, source);
            } else {
                sources.addFirst(source);
            }
        });
    }
}

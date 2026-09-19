package com.liorshaya.policypilot.config;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Scope;
import tools.jackson.core.StreamReadConstraints;
import tools.jackson.core.json.JsonFactory;
import tools.jackson.databind.json.JsonMapper;

/**
 * The limits of every request body the API parses (Document 5, JSON and deserialization: 1 MB, nesting depth 32).
 * Replaces Spring Boot's mapper builder with one on a constrained factory and applies Boot's customizers to it, so
 * the {@code spring.jackson.*} settings still hold.
 */
@Configuration(proxyBeanMethods = false)
public class JsonLimitsConfiguration {

    public static final int MAX_DOCUMENT_BYTES = 1024 * 1024;
    public static final int MAX_NESTING_DEPTH = 32;

    @Bean
    @Scope("prototype")
    JsonMapper.Builder constrainedJsonMapperBuilder(ObjectProvider<JsonMapperBuilderCustomizer> customizers) {
        JsonFactory factory = JsonFactory.builder()
                .streamReadConstraints(StreamReadConstraints.builder()
                        .maxDocumentLength(MAX_DOCUMENT_BYTES)
                        .maxNestingDepth(MAX_NESTING_DEPTH)
                        .build())
                .build();
        JsonMapper.Builder builder = JsonMapper.builder(factory);
        customizers.orderedStream().forEach(customizer -> customizer.customize(builder));
        return builder;
    }
}

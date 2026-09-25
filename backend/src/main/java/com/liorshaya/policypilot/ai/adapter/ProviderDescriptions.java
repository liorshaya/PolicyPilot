package com.liorshaya.policypilot.ai.adapter;

import com.liorshaya.policypilot.ai.ModelRole;
import com.liorshaya.policypilot.ai.ProviderDescription;
import com.liorshaya.policypilot.config.PolicyPilotProperties;
import java.util.Locale;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.PropertyResolver;

/**
 * The active profile's provider as {@code GET /system/provider} names it (Brief FR-21; Document 2, API Surface), built
 * here because this is the one package that reads Spring AI's properties. The provider is the one the gateway records
 * on every call, the role models are the ones it asks, and the embedding model is the one the profile names under its
 * provider, {@code spring.ai.<provider>.embedding.model}: a profile that names none refuses to start rather than put a
 * guess in the header.
 */
@Configuration(proxyBeanMethods = false)
class ProviderDescriptions {

    private static final String EMBEDDING_MODEL = "embeddingmodel";

    @Bean
    ProviderDescription providerDescription(ChatModel chat, EmbeddingModel embedding, Environment environment,
            PolicyPilotProperties properties) {
        return describe(SpringAiLlmGateway.providerOf(chat), providerOf(embedding), environment, properties);
    }

    static ProviderDescription describe(String chatProvider, String embeddingProvider, PropertyResolver environment,
            PolicyPilotProperties properties) {
        return new ProviderDescription(chatProvider,
                SpringAiLlmGateway.modelFor(ModelRole.STRONG, properties),
                SpringAiLlmGateway.modelFor(ModelRole.FAST, properties),
                environment.getRequiredProperty("spring.ai." + embeddingProvider + ".embedding.model"),
                properties.embedding().dimension());
    }

    /** The provider's own name, taken from the model implementation: OpenAiEmbeddingModel is openai, and so on. */
    static String providerOf(EmbeddingModel embedding) {
        String name = embedding.getClass().getSimpleName().toLowerCase(Locale.ROOT);
        return name.endsWith(EMBEDDING_MODEL) ? name.substring(0, name.length() - EMBEDDING_MODEL.length()) : name;
    }
}

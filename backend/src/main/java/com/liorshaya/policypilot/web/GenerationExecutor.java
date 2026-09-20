package com.liorshaya.policypilot.web;

import com.liorshaya.policypilot.config.PolicyPilotProperties;
import com.liorshaya.policypilot.web.security.StreamRegistry;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * What a stream needs to run (Document 2, SSE conventions; Document 5, Availability). Authoring takes tens of
 * seconds, so it runs off the request thread on a virtual thread, because the work waits on a provider rather
 * than on a CPU; and the registry caps how many streams a sandbox may hold open at once.
 */
@Configuration(proxyBeanMethods = false)
public class GenerationExecutor {

    @Bean(destroyMethod = "close")
    ExecutorService generations() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    @Bean
    StreamRegistry streamRegistry(PolicyPilotProperties properties) {
        return new StreamRegistry(properties.rateLimit().concurrentStreams());
    }
}

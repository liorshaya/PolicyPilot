package com.liorshaya.policypilot.support;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * The lines one logger writes, each encoded by Spring Boot's ECS encoder as {@code application.yml} configures it, so
 * a test reads the line Railway receives. An event the encoder refuses is kept as its failure: logback drops such an
 * event without a trace (day 14, {@code rag.embedding.failed} and its duplicate {@code error} key), and a test that
 * only looked at the event would never see it. Close it to detach the appender.
 */
public final class EcsLogCapture implements AutoCloseable {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** One event: the JSON line the encoder wrote, or the exception it threw instead. */
    public record Line(String message, JsonNode json, RuntimeException refused) {}

    private final Logger logger;
    private final Appender appender;

    private EcsLogCapture(Class<?> source) {
        logger = (Logger) LoggerFactory.getLogger(source);
        LoggerContext context = logger.getLoggerContext();
        StructuredLogEncoder encoder = new StructuredLogEncoder();
        encoder.setFormat("ecs");
        encoder.setContext(context);
        encoder.start();
        appender = new Appender(encoder);
        appender.setContext(context);
        appender.start();
        logger.addAppender(appender);
    }

    /** Starts capturing what {@code source}'s logger writes. */
    public static EcsLogCapture of(Class<?> source) {
        return new EcsLogCapture(source);
    }

    /**
     * The first event with this message that {@code which} accepts, waiting up to {@code timeout} for it: a job logs
     * on its own thread. A refused event has no JSON, so it is offered to {@code which} as it is.
     */
    public Optional<Line> await(String message, Predicate<Line> which, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        do {
            Optional<Line> found = appender.lines.stream()
                    .filter(line -> line.message().equals(message) && which.test(line)).findFirst();
            if (found.isPresent()) {
                return found;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        return Optional.empty();
    }

    @Override
    public void close() {
        logger.detachAppender(appender);
        appender.stop();
    }

    private static final class Appender extends AppenderBase<ILoggingEvent> {

        private final StructuredLogEncoder encoder;
        private final List<Line> lines = new CopyOnWriteArrayList<>();

        Appender(StructuredLogEncoder encoder) {
            this.encoder = encoder;
        }

        @Override
        protected void append(ILoggingEvent event) {
            try {
                String line = new String(encoder.encode(event), StandardCharsets.UTF_8);
                lines.add(new Line(event.getMessage(), JSON.readTree(line), null));
            } catch (RuntimeException refused) {
                lines.add(new Line(event.getMessage(), null, refused));
            }
        }
    }
}

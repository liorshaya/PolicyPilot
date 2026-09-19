package com.liorshaya.policypilot.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A plain HTTP client for the running API in integration tests: every header is explicit, so a test shows exactly
 * what the browser would send (cookie, {@code Origin}, {@code X-PolicyPilot-Client}) and which client IP the API sees
 * ({@code X-Forwarded-For}, honoured from the loopback proxy as Railway's proxy is honoured in the cloud).
 */
public final class Api {

    /** The web app's origin in the default allowlist of {@code application.yml}. */
    public static final String WEB_ORIGIN = "http://localhost:5173";
    /** The access code of {@code src/test/resources/config/application.yml}. */
    public static final String ACCESS_CODE = "testcode";

    private static final Pattern SESSION_COOKIE = Pattern.compile("pp_session=([^;]*)");

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public Api(int port) {
        this.base = "http://localhost:" + port;
    }

    public Call get(String path) {
        return new Call("GET", path);
    }

    public Call post(String path) {
        return new Call("POST", path);
    }

    public Call method(String method, String path) {
        return new Call(method, path);
    }

    /** Exchanges the access code from {@code ip} and returns the session cookie value. */
    public String login(String ip) {
        HttpResponse<String> response = post("/api/v1/auth/code").web().from(ip)
                .json("{\"code\":\"" + ACCESS_CODE + "\"}").send();
        return sessionCookie(response).orElseThrow(() -> new IllegalStateException(
                "login failed with HTTP " + response.statusCode() + ": " + response.body()));
    }

    /** The {@code pp_session} value a response sets, if it sets one. */
    public static Optional<String> sessionCookie(HttpResponse<?> response) {
        return response.headers().allValues("Set-Cookie").stream()
                .map(SESSION_COOKIE::matcher)
                .filter(Matcher::find)
                .map(m -> m.group(1))
                .findFirst();
    }

    /** One request under construction. */
    public final class Call {

        private final String method;
        private final String path;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.noBody();

        private Call(String method, String path) {
            this.method = method;
            this.path = path;
        }

        public Call header(String name, String value) {
            headers.put(name, value);
            return this;
        }

        /** The two headers every state-changing request from the web app carries (Document 5, CSRF). */
        public Call web() {
            return header("Origin", WEB_ORIGIN).header("X-PolicyPilot-Client", "web");
        }

        public Call cookie(String session) {
            return header("Cookie", "pp_session=" + session);
        }

        public Call from(String ip) {
            return header("X-Forwarded-For", ip);
        }

        public Call json(String json) {
            header("Content-Type", "application/json");
            body = HttpRequest.BodyPublishers.ofString(json);
            return this;
        }

        public Call body(String contentType, byte[] bytes) {
            header("Content-Type", contentType);
            body = HttpRequest.BodyPublishers.ofByteArray(bytes);
            return this;
        }

        public HttpResponse<String> send() {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).method(method, body);
            headers.forEach(request::header);
            try {
                return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
    }
}

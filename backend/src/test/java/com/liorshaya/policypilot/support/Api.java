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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A plain HTTP client for the running API in integration tests: every header is explicit, so a test shows exactly
 * what the browser would send (cookie, {@code Origin}, {@code X-PolicyPilot-Client}) and which client IP the API sees
 * ({@code X-Forwarded-For}, honoured from the loopback proxy as Railway's proxy is honoured in the cloud). A request
 * whose test names no IP comes from an address of its own.
 */
public final class Api {

    /**
     * The one address the test server listens on and every client calls. Not {@code localhost}: with the server on
     * every address, another listener on this address and the same ephemeral port (a port Docker forwards) answered
     * one run's sign-ins with 404 (day 14); bound to this address alone, the port the server gets is its own.
     */
    public static final String HOST = "127.0.0.1";
    /** The web app's origin in the default allowlist of {@code application.yml}. */
    public static final String WEB_ORIGIN = "http://localhost:5173";
    /** The access code of {@code src/test/resources/config/application.yml}. */
    public static final String ACCESS_CODE = "testcode";

    private static final Pattern SESSION_COOKIE = Pattern.compile("pp_session=([^;]*)");
    /** Requests without an explicit client IP each come from their own address, so no two tests share a bucket. */
    private static final AtomicInteger NEXT_CLIENT = new AtomicInteger();

    private final HttpClient http = HttpClient.newHttpClient();
    private final String base;

    public Api(int port) {
        this.base = base(port);
    }

    /** The test server's base URL on {@link #HOST}. */
    public static String base(int port) {
        return "http://" + HOST + ":" + port;
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

    /** Exchanges the access code from an address of its own and returns the session cookie value. */
    public String login() {
        return login(post("/api/v1/auth/code"));
    }

    /** Exchanges the access code from {@code ip} and returns the session cookie value. */
    public String login(String ip) {
        return login(post("/api/v1/auth/code").from(ip));
    }

    private String login(Call exchange) {
        HttpResponse<String> response = exchange.web().json("{\"code\":\"" + ACCESS_CODE + "\"}").send();
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
            int client = NEXT_CLIENT.incrementAndGet();
            from("10." + (client >> 16 & 0xff) + "." + (client >> 8 & 0xff) + "." + (client & 0xff));
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

        /** A multipart form with the policy fields and one file part, as the web app's upload sends it. */
        public Call multipart(String title, String language, String fileName, byte[] file) {
            String boundary = "policypilot-test-boundary";
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            java.nio.charset.Charset utf8 = java.nio.charset.StandardCharsets.UTF_8;
            for (String[] field : new String[][] {{"title", title}, {"language", language}}) {
                out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + field[0]
                        + "\"\r\nContent-Type: text/plain; charset=UTF-8\r\n\r\n" + field[1] + "\r\n").getBytes(utf8));
            }
            out.writeBytes(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName
                    + "\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(utf8));
            out.writeBytes(file);
            out.writeBytes(("\r\n--" + boundary + "--\r\n").getBytes(utf8));
            return body("multipart/form-data; boundary=" + boundary, out.toByteArray());
        }

        public Call body(String contentType, byte[] bytes) {
            header("Content-Type", contentType);
            body = HttpRequest.BodyPublishers.ofByteArray(bytes);
            return this;
        }

        /** The response as its lines arrive, for a test that times a stream rather than reading it whole. */
        public HttpResponse<Stream<String>> lines() {
            HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(base + path)).method(method, body);
            headers.forEach(request::header);
            try {
                return http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
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

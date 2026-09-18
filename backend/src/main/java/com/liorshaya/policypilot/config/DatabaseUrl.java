package com.liorshaya.policypilot.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

/**
 * A PostgreSQL connection URL in the form managed platforms hand out ({@code postgresql://user:password@host:port/db}),
 * translated into what the JDBC driver expects.
 *
 * <p>Railway provides {@code DATABASE_URL} in that form (Document 2, Deployment Topology); Docker Compose uses the
 * same form so one variable name works everywhere. A value that already starts with {@code jdbc:} needs no
 * translation and is left alone.
 */
public record DatabaseUrl(String jdbcUrl, String username, String password) {

    private static final int DEFAULT_PORT = 5432;

    /**
     * @param value the raw {@code DATABASE_URL}
     * @return the translated connection details, or empty when the value is not a {@code postgres://} or
     *     {@code postgresql://} URL (including a JDBC URL, which Spring reads as it is)
     */
    public static Optional<DatabaseUrl> parse(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("postgres://") && !trimmed.startsWith("postgresql://")) {
            return Optional.empty();
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("DATABASE_URL is not a valid URL", e);
        }
        if (uri.getHost() == null || uri.getPath() == null || uri.getPath().length() <= 1) {
            throw new IllegalArgumentException("DATABASE_URL must name a host and a database: postgresql://user:password@host:5432/db");
        }
        int port = uri.getPort() == -1 ? DEFAULT_PORT : uri.getPort();
        String database = uri.getPath().substring(1);
        String query = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        String jdbcUrl = "jdbc:postgresql://" + uri.getHost() + ":" + port + "/" + database + query;

        String username = null;
        String password = null;
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && !userInfo.isEmpty()) {
            int colon = userInfo.indexOf(':');
            username = decode(colon < 0 ? userInfo : userInfo.substring(0, colon));
            password = colon < 0 ? null : decode(userInfo.substring(colon + 1));
        }
        return Optional.of(new DatabaseUrl(jdbcUrl, username, password));
    }

    private static String decode(String component) {
        return URLDecoder.decode(component.replace("+", "%2B"), StandardCharsets.UTF_8);
    }
}

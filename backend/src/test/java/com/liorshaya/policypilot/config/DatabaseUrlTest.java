package com.liorshaya.policypilot.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DatabaseUrlTest {

    @Test
    void railwayPrivateUrlBecomesJdbcUrlWithCredentials() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://postgres:s3cret@postgres.railway.internal:5432/railway").orElseThrow();

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://postgres.railway.internal:5432/railway");
        assertThat(url.username()).isEqualTo("postgres");
        assertThat(url.password()).isEqualTo("s3cret");
    }

    @Test
    void postgresSchemeWithoutPortDefaultsTo5432() {
        DatabaseUrl url = DatabaseUrl.parse("postgres://policypilot:policypilot@db/policypilot").orElseThrow();

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://db:5432/policypilot");
    }

    @Test
    void queryStringIsKeptOnTheJdbcUrl() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://u:p@host:6543/db?sslmode=require").orElseThrow();

        assertThat(url.jdbcUrl()).isEqualTo("jdbc:postgresql://host:6543/db?sslmode=require");
    }

    @Test
    void percentEncodedPasswordIsDecoded() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://u:p%40ss%3Aword@host/db").orElseThrow();

        assertThat(url.password()).isEqualTo("p@ss:word");
    }

    @Test
    void userWithoutPasswordLeavesPasswordNull() {
        DatabaseUrl url = DatabaseUrl.parse("postgresql://readonly@host/db").orElseThrow();

        assertThat(url.username()).isEqualTo("readonly");
        assertThat(url.password()).isNull();
    }

    @Test
    void jdbcUrlNeedsNoTranslation() {
        assertThat(DatabaseUrl.parse("jdbc:postgresql://localhost:5432/policypilot")).isEmpty();
    }

    @Test
    void blankAndForeignValuesAreIgnored() {
        assertThat(DatabaseUrl.parse(null)).isEmpty();
        assertThat(DatabaseUrl.parse("   ")).isEmpty();
        assertThat(DatabaseUrl.parse("mysql://u:p@host/db")).isEmpty();
    }

    @Test
    void urlWithoutDatabaseIsRejectedLoudly() {
        assertThatThrownBy(() -> DatabaseUrl.parse("postgresql://u:p@host:5432"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("host and a database");
    }
}

package com.axioma.loyalty.config;

import io.r2dbc.pool.ConnectionPool;
import io.r2dbc.pool.ConnectionPoolConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.postgresql.client.SSLMode;
import io.r2dbc.spi.ConnectionFactory;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(DatabaseProperties.class)
public class DatabaseConfig {

    @Bean
    public PostgresUrl postgresUrl(DatabaseProperties properties) {
        return PostgresUrl.parse(properties.url());
    }

    @Bean
    public ConnectionFactory connectionFactory(PostgresUrl url, DatabaseProperties properties) {
        var postgres = new PostgresqlConnectionFactory(
                PostgresqlConnectionConfiguration.builder()
                        .host(url.host())
                        .port(url.port())
                        .database(url.database())
                        .username(url.username())
                        .password(url.password())
                        .sslMode(url.sslRequired() ? SSLMode.REQUIRE : SSLMode.DISABLE)
                        .build());
        return new ConnectionPool(
                ConnectionPoolConfiguration.builder(postgres)
                        .initialSize(1)
                        .maxSize(properties.poolMaxSize())
                        // Neon cierra conexiones al suspender el compute: no conservar las viejas.
                        .maxIdleTime(Duration.ofMinutes(4))
                        .maxLifeTime(Duration.ofMinutes(30))
                        .validationQuery("SELECT 1")
                        .build());
    }

    /**
     * Migraciones versionadas (src/main/resources/db/migration). Corre al arrancar, antes de
     * recibir tráfico. Si las tablas ya existían sin historial de Flyway (la base de pruebas
     * se creó a mano con este mismo esquema), se toma como línea base en la versión 1.
     */
    @Bean
    public Flyway flyway(PostgresUrl url, DatabaseProperties properties) {
        Flyway flyway = Flyway.configure()
                .dataSource(url.jdbcUrl(), url.username(), url.password())
                .schemas("loyalty", "wallet")
                .defaultSchema("loyalty")
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1")
                .load();
        if (properties.migrateOnStartup()) {
            var result = flyway.migrate();
            log.info("Migraciones aplicadas: {}", result.migrationsExecuted);
        }
        return flyway;
    }
}

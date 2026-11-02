package dev.hustletech.loopmarket.app;

import com.codahale.metrics.health.HealthCheck;
import javax.sql.DataSource;
import java.sql.Connection;

// Reports whether MySQL can accept a short validation query, not merely whether a socket opened.
public final class DatabaseHealthCheck extends HealthCheck {
    private final DataSource database;

    public DatabaseHealthCheck(DataSource database) {
        this.database = database;
    }

    @Override
    protected Result check() throws Exception {
        try (Connection connection = database.getConnection()) {
            return connection.isValid(2) ? Result.healthy() : Result.unhealthy("Database connection is invalid");
        }
    }
}

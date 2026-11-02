package dev.hustletech.loopmarket.app;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseHealthCheckTest {
    @Test
    void checksWhetherAConnectionCanBeOpened() {
        JdbcDataSource available = new JdbcDataSource();
        available.setURL("jdbc:h2:mem:healthcheck");
        assertTrue(new DatabaseHealthCheck(available).execute().isHealthy());

        JdbcDataSource unavailable = new JdbcDataSource();
        unavailable.setURL("jdbc:h2:mem:missing;IFEXISTS=TRUE");
        assertFalse(new DatabaseHealthCheck(unavailable).execute().isHealthy());
    }
}

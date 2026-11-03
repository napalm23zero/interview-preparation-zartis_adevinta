package dev.hustletech.loopmarket.app;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import static org.junit.jupiter.api.Assertions.*;

// A separate empty database proves production migrations leave the demo fleet behind.
@EnabledIfEnvironmentVariable(named = "LOOPMARKET_EMPTY_PROD_TEST_URL", matches = ".+")
class DemoSeedIsolationIntegrationTest {
    @Test void productionContextDoesNotCreateDemoData() throws Exception {
        MysqlDataSource db = new MysqlDataSource();
        db.setURL(System.getenv("LOOPMARKET_EMPTY_PROD_TEST_URL"));
        db.setUser(System.getenv("LOOPMARKET_EMPTY_PROD_TEST_USER"));
        db.setPassword(System.getenv("LOOPMARKET_EMPTY_PROD_TEST_PASSWORD"));
        DatabaseMigrations.run(db, "", "https://identity.example.invalid");
        DatabaseMigrations.run(db, "", "https://identity.example.invalid");
        try (Connection connection = db.getConnection(); var query = connection.createStatement()) {
            for (String table : new String[]{"persons", "users", "listings", "listing_photos", "interests", "generation_attempts", "listing_requests"}) {
                try (var rows = query.executeQuery("SELECT COUNT(*) FROM " + table)) {
                    assertTrue(rows.next()); assertEquals(0, rows.getLong(1), table);
                }
            }
        }
    }
}

package dev.hustletech.loopmarket.app;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.Connection;
import java.sql.PreparedStatement;
import static org.junit.jupiter.api.Assertions.assertEquals;

@EnabledIfEnvironmentVariable(named = "LOOPMARKET_E2E", matches = "true")
class DatabaseMigrationsIntegrationTest {
    @Test void devSeedsArePresentAfterRepeatedMigrations() throws Exception {
        String host = System.getenv("MYSQL_HOST");
        String port = System.getenv("MYSQL_PORT");
        String name = System.getenv("MYSQL_DATABASE");
        String issuer = "http://identity:" + System.getenv("IDENTITY_PORT") + "/realms/loopmarket-dev";
        MysqlDataSource database = new MysqlDataSource();
        database.setURL("jdbc:mysql://" + host + ":" + port + "/" + name);
        database.setUser(System.getenv("MYSQL_USER"));
        database.setPassword(System.getenv("MYSQL_PASSWORD"));
        DatabaseMigrations.run(database, "dev", issuer);
        DatabaseMigrations.run(database, "dev", issuer);
        try (Connection connection = database.getConnection();
             PreparedStatement query = connection.prepareStatement(
                     "SELECT COUNT(*) FROM users WHERE issuer = ? AND deleted_at IS NULL AND ((external_subject = ? AND account_role = 'ADMIN') OR (external_subject = ? AND account_role = 'USER'))")) {
            query.setString(1, issuer);
            query.setString(2, "01900000-0000-7000-8000-000000000201");
            query.setString(3, "01900000-0000-7000-8000-000000000202");
            try (var result = query.executeQuery()) { result.next(); assertEquals(2, result.getLong(1)); }
        }
    }
}

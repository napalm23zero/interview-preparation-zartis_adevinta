package dev.hustletech.loopmarket.app;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;

import javax.sql.DataSource;
import java.sql.Connection;

final class DatabaseMigrations {
    private DatabaseMigrations() {}

    static void run(DataSource source, String contexts, String issuer) throws Exception {
        // Liquibase owns schema evolution; run it on the managed datasource before serving requests.
        try (Connection connection = source.getConnection()) {
            Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (Liquibase liquibase = new Liquibase("db/changelog.xml", new ClassLoaderResourceAccessor(), database)) {
                // Keep seeded identity subjects aligned with the environment's provider realm.
                liquibase.setChangeLogParameter("identityIssuer", issuer);
                liquibase.update(new Contexts(contexts), new LabelExpression());
            }
        }
    }
}

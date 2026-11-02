package dev.hustletech.loopmarket.app;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.dropwizard.core.Configuration;
import io.dropwizard.db.DataSourceFactory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Maps YAML and environment substitutions into validated settings for the application and its adapters.
public final class LoopMarketConfiguration extends Configuration {
    private boolean migrationsEnabled;
    private String migrationContexts = "";
    private String identityIssuer = "";
    private boolean sessionsEnabled;
    private String redisUrl = "redis://localhost:6379";
    private boolean demoUsersEnabled;
    private String identityAdminUser = "";
    private String identityAdminPassword = "";
    private String photoStoragePath = "/data/photos";
    @Valid
    @NotNull
    private DataSourceFactory database = new DataSourceFactory();

    @JsonProperty("database")
    public DataSourceFactory getDatabase() {
        return database;
    }

    @JsonProperty("database")
    public void setDatabase(DataSourceFactory database) {
        this.database = database;
    }

    @JsonProperty public boolean isMigrationsEnabled() { return migrationsEnabled; }
    @JsonProperty public void setMigrationsEnabled(boolean enabled) { this.migrationsEnabled = enabled; }
    @JsonProperty public String getMigrationContexts() { return migrationContexts; }
    @JsonProperty public void setMigrationContexts(String contexts) { this.migrationContexts = contexts; }
    @JsonProperty public String getIdentityIssuer() { return identityIssuer; }
    @JsonProperty public void setIdentityIssuer(String issuer) { this.identityIssuer = issuer; }
    @JsonProperty public boolean isSessionsEnabled() { return sessionsEnabled; }
    @JsonProperty public void setSessionsEnabled(boolean enabled) { this.sessionsEnabled = enabled; }
    @JsonProperty public String getRedisUrl() { return redisUrl; }
    @JsonProperty public void setRedisUrl(String url) { this.redisUrl = url; }
    @JsonProperty public boolean isDemoUsersEnabled() { return demoUsersEnabled; }
    @JsonProperty public void setDemoUsersEnabled(boolean enabled) { this.demoUsersEnabled = enabled; }
    @JsonProperty public String getIdentityAdminUser() { return identityAdminUser; }
    @JsonProperty public void setIdentityAdminUser(String user) { this.identityAdminUser = user; }
    @JsonProperty public String getIdentityAdminPassword() { return identityAdminPassword; }
    @JsonProperty public void setIdentityAdminPassword(String password) { this.identityAdminPassword = password; }
    @JsonProperty public String getPhotoStoragePath() { return photoStoragePath; }
    @JsonProperty public void setPhotoStoragePath(String path) { this.photoStoragePath = path; }
}

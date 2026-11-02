package dev.hustletech.loopmarket.app;

import dev.hustletech.loopmarket.marketplace.adapter.http.GreetingResource;
import dev.hustletech.loopmarket.marketplace.application.GreetService;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.core.Application;
import io.dropwizard.core.setup.Bootstrap;
import io.dropwizard.core.setup.Environment;
import io.dropwizard.db.ManagedDataSource;

public final class LoopMarketApplication extends Application<LoopMarketConfiguration> {
    public static void main(String[] args) throws Exception {
        new LoopMarketApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<LoopMarketConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(true)));
        bootstrap.addBundle(new AssetsBundle("/openapi", "/api/spec", "openapi.yaml", "openapi"));
        bootstrap.addBundle(new AssetsBundle("/swagger", "/api/docs", "index.html", "swagger-docs"));
        bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars/swagger-ui/5.19.0", "/api/swagger-ui", "index.html", "swagger-ui"));
    }

    @Override
    public void run(LoopMarketConfiguration configuration, Environment environment) {
        ManagedDataSource database = configuration.getDatabase().build(environment.metrics(), "mysql");
        environment.lifecycle().manage(database);
        environment.healthChecks().register("database", new DatabaseHealthCheck(database));
        environment.jersey().register(new GreetingResource(new GreetService()));
    }
}

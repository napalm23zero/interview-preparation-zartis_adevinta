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
import dev.hustletech.loopmarket.identity.adapter.redis.RedisSessionStore;
import dev.hustletech.loopmarket.identity.application.SessionService;
import io.dropwizard.lifecycle.Managed;
import redis.clients.jedis.RedisClient;
import java.security.SecureRandom;
import java.time.Clock;
import dev.hustletech.loopmarket.app.person.PersonEntity;
import dev.hustletech.loopmarket.app.person.PersonResource;
import dev.hustletech.loopmarket.app.person.PersonService;
import dev.hustletech.loopmarket.shared.adapter.persistence.GenericHibernateRepository;
import io.dropwizard.hibernate.HibernateBundle;
import io.dropwizard.db.DataSourceFactory;
import dev.hustletech.loopmarket.app.account.AccountEntity;
import dev.hustletech.loopmarket.app.account.AccountResource;
import dev.hustletech.loopmarket.app.account.AccountService;
import dev.hustletech.loopmarket.app.listing.ListingEntity;
import dev.hustletech.loopmarket.app.listing.ListingResource;
import dev.hustletech.loopmarket.app.interest.InterestEntity;
import dev.hustletech.loopmarket.app.interest.InterestResource;
import dev.hustletech.loopmarket.app.photo.PhotoEntity;
import dev.hustletech.loopmarket.app.photo.PhotoResource;
import dev.hustletech.loopmarket.app.generation.GenerationEntity;
import dev.hustletech.loopmarket.app.generation.GenerationResource;

// Composition root: wires infrastructure, migrations, identity, and product resources into Dropwizard.
public final class LoopMarketApplication extends Application<LoopMarketConfiguration> {
    // Register every mapped class here so Hibernate can validate and manage the complete marketplace model.
    private final HibernateBundle<LoopMarketConfiguration> hibernate = new HibernateBundle<>(PersonEntity.class, AccountEntity.class, ListingEntity.class, InterestEntity.class, PhotoEntity.class, GenerationEntity.class) {
        @Override public DataSourceFactory getDataSourceFactory(LoopMarketConfiguration configuration) {
            return configuration.getDatabase();
        }
    };
    public static void main(String[] args) throws Exception {
        new LoopMarketApplication().run(args);
    }

    @Override
    public void initialize(Bootstrap<LoopMarketConfiguration> bootstrap) {
        // Load environment-backed settings and publish the API contract plus its Swagger interface.
        bootstrap.addBundle(hibernate);
        bootstrap.setConfigurationSourceProvider(new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(), new EnvironmentVariableSubstitutor(true)));
        bootstrap.addBundle(new AssetsBundle("/openapi", "/api/spec", "openapi.yaml", "openapi"));
        bootstrap.addBundle(new AssetsBundle("/swagger", "/api/docs", "index.html", "swagger-docs"));
        bootstrap.addBundle(new AssetsBundle("/META-INF/resources/webjars/swagger-ui/5.19.0", "/api/swagger-ui", "index.html", "swagger-ui"));
    }

    @Override
    public void run(LoopMarketConfiguration configuration, Environment environment) throws Exception {
        // Register managed dependencies first so Dropwizard can close them with the application lifecycle.
        ManagedDataSource database = configuration.getDatabase().build(environment.metrics(), "mysql");
        environment.lifecycle().manage(database);
        if (configuration.isMigrationsEnabled()) {
            DatabaseMigrations.run(database, configuration.getMigrationContexts(), configuration.getIdentityIssuer());
        }
        environment.healthChecks().register("database", new DatabaseHealthCheck(database));
        environment.jersey().register(new OptimisticLockMapper());
        environment.jersey().register(new DatabaseConstraintMapper());
        // Resources build caller-scoped services; shared CRUD stays generic without weakening each boundary.
        environment.jersey().register(new PersonResource(new PersonService(
                new GenericHibernateRepository<>(hibernate.getSessionFactory(), PersonEntity.class), Clock.systemUTC(), hibernate.getSessionFactory())));
        environment.jersey().register(new AccountResource(new AccountService(
                new GenericHibernateRepository<>(hibernate.getSessionFactory(), AccountEntity.class), Clock.systemUTC(), hibernate.getSessionFactory())));
        environment.jersey().register(new ListingResource(hibernate.getSessionFactory()));
        environment.jersey().register(new InterestResource(hibernate.getSessionFactory()));
        environment.jersey().register(new PhotoResource(hibernate.getSessionFactory(), java.nio.file.Path.of(configuration.getPhotoStoragePath())));
        environment.jersey().register(new GenerationResource(hibernate.getSessionFactory()));
        if (configuration.isSessionsEnabled()) {
            // Sessions are optional for local smoke tests; when enabled, Redis and the filter protect requests.
            RedisClient redis = RedisClient.create(configuration.getRedisUrl());
            environment.lifecycle().manage(new Managed() {
                @Override public void start() { }
                @Override public void stop() { redis.close(); }
            });
            SessionService sessions = new SessionService(new RedisSessionStore(redis), Clock.systemUTC(), new SecureRandom());
            environment.jersey().register(new SessionFilter(sessions, database));
            environment.jersey().register(new SessionResource(sessions, database, configuration.getIdentityIssuer(), environment.getObjectMapper()));
        }
        if (configuration.isDemoUsersEnabled()) {
            environment.lifecycle().manage(new DemoIdentityProvisioner(configuration.getIdentityIssuer(), configuration.getIdentityAdminUser(),
                    configuration.getIdentityAdminPassword(), environment.getObjectMapper()));
        }
        environment.jersey().register(new GreetingResource(new GreetService()));
    }
}

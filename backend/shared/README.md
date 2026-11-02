# Shared CRUD foundation

This module provides reusable entity audit fields, a repository port, a Hibernate adapter, a CRUD service and a Dropwizard resource. It does not register endpoints or configure a Hibernate session factory; those are added when the first product entity is implemented.

- IDs are UUIDv7, and audit fields are `Instant` values in UTC. Delete is a soft delete; generic reads exclude deleted records.
- The resource provides create, read, list, update and delete. Lists use one-based pages, default to 10 items and cap the page size at 50.
- Request and response types belong to each concrete resource. Map entities to response types inside the transaction rather than returning managed entities.
- A concrete resource must implement `authorizedService` and enforce permissions and ownership for every operation. List and count must use a repository scoped to the caller. The default Hibernate repository only filters deleted records and must not be used as an ownership boundary.
- Hibernate-backed operations require a configured Dropwizard `HibernateBundle` and an active `@UnitOfWork`. Verify transaction handling and inherited resource annotations when registering the first concrete resource.

Restore, hard delete and dynamic search are outside the current MVP.

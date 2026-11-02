package dev.hustletech.loopmarket.marketplace.adapter.http;

import dev.hustletech.loopmarket.marketplace.application.Greet;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
// This adapter maps the use-case result to the public JSON contract and keeps JAX-RS at the edge.
public final class GreetingResource {
    private final Greet greet;

    public GreetingResource(Greet greet) {
        this.greet = greet;
    }

    @GET
    public GreetingResponse hello() {
        return new GreetingResponse(greet.execute().message());
    }

    public record GreetingResponse(String message) {}
}

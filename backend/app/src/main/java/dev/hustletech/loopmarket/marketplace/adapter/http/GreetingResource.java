package dev.hustletech.loopmarket.marketplace.adapter.http;

import dev.hustletech.loopmarket.marketplace.application.Greet;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/hello")
@Produces(MediaType.APPLICATION_JSON)
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

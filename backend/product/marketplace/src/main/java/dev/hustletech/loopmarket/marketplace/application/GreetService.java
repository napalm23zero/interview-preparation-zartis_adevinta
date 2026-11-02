package dev.hustletech.loopmarket.marketplace.application;

import dev.hustletech.loopmarket.marketplace.domain.Greeting;

// The first use case stays deliberately tiny: create a domain result, then let the adapter present it.
public final class GreetService implements Greet {
    @Override
    public Greeting execute() {
        return new Greeting("Hello, World!");
    }
}

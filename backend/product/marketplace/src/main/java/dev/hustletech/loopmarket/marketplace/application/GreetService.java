package dev.hustletech.loopmarket.marketplace.application;

import dev.hustletech.loopmarket.marketplace.domain.Greeting;

public final class GreetService implements Greet {
    @Override
    public Greeting execute() {
        return new Greeting("Hello, World!");
    }
}

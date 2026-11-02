package dev.hustletech.loopmarket.marketplace.application;

import dev.hustletech.loopmarket.marketplace.domain.Greeting;

// This application port lets the HTTP adapter call the use case without knowing its implementation.
public interface Greet {
    Greeting execute();
}

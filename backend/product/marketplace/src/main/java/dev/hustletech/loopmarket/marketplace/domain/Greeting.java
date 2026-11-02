package dev.hustletech.loopmarket.marketplace.domain;

// A transport-free value keeps the use case independent of HTTP; the Force does not need a framework here.
public record Greeting(String message) {}

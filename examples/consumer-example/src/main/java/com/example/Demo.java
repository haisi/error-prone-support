package com.example;

import java.time.Instant;

/** Compiles cleanly - none of these uses trip the configured forbidden signatures. */
public final class Demo {

    private final Instant createdAt = Instant.now();

    public Instant createdAt() {
        return createdAt;
    }
}

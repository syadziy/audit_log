package com.mac.audit.utils.handler;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AsyncExceptionHandlerTest {

    @Test
    void acceptsGeneratedExplicitTraceAndAdditionalFields() {
        AsyncExceptionHandler handler = new AsyncExceptionHandler();
        handler.handle(null, "audit.dataset", "test", "run", null, new IllegalStateException("failure"));
        handler.handle("trace", "audit.dataset", "test", "run", Map.of("event.id", "1"),
                new IllegalStateException("failure"));
    }
}

package com.mac.audit.utils.handler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.mac.audit.entities.model.ErrorAlert;
import com.mac.audit.service.ErrorAlertNotifier;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AsyncExceptionHandlerTest {

    @Test
    void acceptsGeneratedExplicitTraceAndAdditionalFields() {
        ErrorAlertNotifier notifier = mock(ErrorAlertNotifier.class);
        AsyncExceptionHandler handler = new AsyncExceptionHandler(notifier);
        handler.handle(null, "audit.dataset", "test", "run", null, new IllegalStateException("failure"));
        handler.handle("trace", "audit.dataset", "test", "run", Map.of("event.id", "1"),
                new IllegalStateException("failure"));
        verify(notifier, times(2)).send(any(ErrorAlert.class));
    }
}

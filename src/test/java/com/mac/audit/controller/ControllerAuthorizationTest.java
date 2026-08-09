package com.mac.audit.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class ControllerAuthorizationTest {

    @Test
    void readEndpointsDeclareTheirPermissionOnTheController() {
        assertPermission("findById");
        assertPermission("find");
    }

    private static void assertPermission(String methodName) {
        PreAuthorize annotation = method(AuditLogController.class, methodName)
                .getAnnotation(PreAuthorize.class);
        assertEquals("hasAuthority('PERM_audit:read')", annotation.value());
    }

    private static Method method(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredMethods())
                .filter(method -> method.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }
}

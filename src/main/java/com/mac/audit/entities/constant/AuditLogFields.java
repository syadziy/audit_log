package com.mac.audit.entities.constant;

public final class AuditLogFields {

    public static final String EVENT_ID = "audit.event.id";
    public static final String SOURCE_SYSTEM = "audit.source.system";
    public static final String ACTION = "audit.action";
    public static final String OUTCOME = "audit.outcome";
    public static final String DUPLICATE = "audit.duplicate";
    public static final String KAFKA_TOPIC = "kafka.topic";
    public static final String KAFKA_PARTITION = "kafka.partition";
    public static final String KAFKA_OFFSET = "kafka.offset";
    public static final String KAFKA_DLT_TOPIC = "kafka.dead_letter.topic";

    private AuditLogFields() {}
}

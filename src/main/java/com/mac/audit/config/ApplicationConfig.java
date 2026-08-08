package com.mac.audit.config;

import com.mac.audit.config.properties.AuditQueryProperties;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuditQueryProperties.class)
public class ApplicationConfig {

    @Bean
    public Clock auditClock() {
        return Clock.systemUTC();
    }
}

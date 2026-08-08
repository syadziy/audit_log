package com.mac.audit.config;

import com.mac.audit.config.properties.AuditQueryProperties;
import com.mac.audit.config.properties.ErrorAlertProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({AuditQueryProperties.class, ErrorAlertProperties.class})
public class ApplicationConfig {

    @Bean
    public Clock auditClock() {
        return Clock.systemUTC();
    }

    @Bean
    public HttpClient auditAlertHttpClient(ErrorAlertProperties properties) {
        return HttpClient.newBuilder()
                .connectTimeout(properties.timeout())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }
}

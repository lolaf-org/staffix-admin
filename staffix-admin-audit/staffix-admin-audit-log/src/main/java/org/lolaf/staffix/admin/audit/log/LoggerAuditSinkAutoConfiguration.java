package org.lolaf.staffix.admin.audit.log;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class LoggerAuditSinkAutoConfiguration {

    @Bean
    LoggerAuditSink loggerAuditSink() {
        return new LoggerAuditSink();
    }
}

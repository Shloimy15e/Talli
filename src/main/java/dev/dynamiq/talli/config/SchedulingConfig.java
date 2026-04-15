package dev.dynamiq.talli.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on Spring's @Scheduled support. Without this annotation, methods
 * marked @Scheduled are silently ignored — Spring needs to know to scan
 * for them and start the background TaskScheduler.
 */
@Configuration
@EnableScheduling
public class SchedulingConfig {
}

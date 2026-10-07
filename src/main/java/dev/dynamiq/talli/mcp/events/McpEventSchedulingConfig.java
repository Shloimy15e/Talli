package dev.dynamiq.talli.mcp.events;

import org.springframework.context.annotation.Bean;
import org.springframework.boot.task.ThreadPoolTaskSchedulerBuilder;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class McpEventSchedulingConfig {
    // Supplying an event scheduler disables Boot's default scheduler auto-creation.
    // Keep the conventional scheduler for existing billing and reminder jobs.
    @Bean
    public ThreadPoolTaskScheduler taskScheduler(ThreadPoolTaskSchedulerBuilder builder) {
        return builder.build();
    }
    @Bean
    public ThreadPoolTaskScheduler mcpEventTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(1);
        scheduler.setThreadNamePrefix("mcp-events-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(15);
        return scheduler;
    }
}

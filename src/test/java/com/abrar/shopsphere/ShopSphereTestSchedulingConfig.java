
package com.abrar.shopsphere;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;

@TestConfiguration
public class ShopSphereTestSchedulingConfig
        implements SchedulingConfigurer {

    @Override
    public void configureTasks(
            ScheduledTaskRegistrar taskRegistrar) {
        // Intentionally do not register scheduled tasks in tests.
    }
}

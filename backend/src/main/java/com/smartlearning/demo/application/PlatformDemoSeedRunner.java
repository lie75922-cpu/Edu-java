package com.smartlearning.demo.application;

import com.smartlearning.common.config.DemoSeedProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;

/** Startup-only local-demo fixture hook, disabled unless APP_DEMO_SEED_ENABLED is true. */
@Configuration
public class PlatformDemoSeedRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlatformDemoSeedRunner.class);

    @Bean
    @Order(100)
    ApplicationRunner platformDemoSeed(DemoSeedProperties properties, PlatformDemoSeedService demoSeedService) {
        return args -> {
            if (!properties.enabled()) {
                return;
            }
            PlatformDemoSeedService.DemoContext context = demoSeedService.seedCoreData();
            demoSeedService.finalizeOperationalDemo(context);
            LOGGER.info(
                    "Synthetic Platform Demo seed ready: courseAId={}, courseBId={}, learningPathTargetKnowledgePointId={}",
                    context.courseAId(), context.courseBId(), context.learningPathTargetKnowledgePointId()
            );
        };
    }
}

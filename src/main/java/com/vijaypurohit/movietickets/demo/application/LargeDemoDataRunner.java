package com.vijaypurohit.movietickets.demo.application;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "app.demo", name = "large-enabled", havingValue = "true")
public class LargeDemoDataRunner implements ApplicationRunner {
    private final LargeDemoDataGenerator generator;
    private final boolean includeHistory;

    public LargeDemoDataRunner(LargeDemoDataGenerator generator,
            @Value("${app.demo.history-enabled:false}") boolean includeHistory) {
        this.generator = generator;
        this.includeHistory = includeHistory;
    }

    @Override
    public void run(ApplicationArguments arguments) {
        generator.generate(includeHistory);
    }
}

package com.foodybuddy.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

@SpringBootApplication
public class FoodybuddyOrdersApplication {

    private static final Logger logger = LoggerFactory.getLogger(FoodybuddyOrdersApplication.class);

    public static void main(String[] args) {
        logger.info("Starting FoodyBuddy Orders Application...");
        
        try {
            ConfigurableApplicationContext context = SpringApplication.run(FoodybuddyOrdersApplication.class, args);
            logger.info("FoodyBuddy Orders Application started successfully on port: {}", 
                context.getEnvironment().getProperty("server.port", "8081"));
        } catch (Exception e) {
            logger.error("Failed to start FoodyBuddy Orders Application", e);
            System.exit(1);
        }
    }

}

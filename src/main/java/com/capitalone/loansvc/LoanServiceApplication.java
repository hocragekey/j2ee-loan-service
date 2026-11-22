package com.capitalone.loansvc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/**
 * Spring Boot 3 entry point. Runs as an executable WAR (embedded Tomcat) and can still be
 * deployed to an external Jakarta EE 10 servlet container (Tomcat 10.1+).
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class LoanServiceApplication extends SpringBootServletInitializer {

    public static void main(String[] args) {
        SpringApplication.run(LoanServiceApplication.class, args);
    }

    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder builder) {
        return builder.sources(LoanServiceApplication.class);
    }
}

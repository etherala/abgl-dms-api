package com.abgl.dms;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.abgl.dms"})
@EnableScheduling
public class AbglDmsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbglDmsApiApplication.class, args);
    }

}

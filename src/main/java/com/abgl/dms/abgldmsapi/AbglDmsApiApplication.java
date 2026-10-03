package com.abgl.dms.abgldmsapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {"com.abgl.dms"})
@EnableScheduling
public class AbglDmsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AbglDmsApiApplication.class, args);
    }

}

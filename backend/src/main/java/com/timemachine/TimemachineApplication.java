package com.timemachine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class TimemachineApplication {
    public static void main(String[] args) {
        SpringApplication.run(TimemachineApplication.class, args);
    }
}

package com.vijaypurohit.movietickets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MovieTicketsApplication {

    public static void main(String[] args) {
        SpringApplication.run(MovieTicketsApplication.class, args);
    }
}

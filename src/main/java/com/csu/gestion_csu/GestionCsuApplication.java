package com.csu.gestion_csu;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GestionCsuApplication {
    public static void main(String[] args) {
        SpringApplication.run(GestionCsuApplication.class, args);
    }
}

package com.roddy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class RoddyApplication {

	public static void main(String[] args) {
		SpringApplication.run(RoddyApplication.class, args);
	}

}

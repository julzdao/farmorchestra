package com.farmorchestra.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point. Starts Spring Boot, which starts the web server, connects the database,
 * runs Flyway migrations and wires every {@code @Component} together.
 */
@SpringBootApplication
public class FarmOrchestraApplication {

	/**
     * Starts the API.
     *
     * @param args command-line arguments, passed to Spring so you can override settings
     *             (for example {@code --server.port=9000})
     */
	public static void main(String[] args) {
		 SpringApplication.run(FarmOrchestraApplication.class, args);
	}

}

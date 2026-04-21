package dev.dynamiq.talli;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TalliApplication {

	public static void main(String[] args) {
		// All LocalDateTime.now() calls produce UTC wall-clock; templates emit ISO with 'Z'
		// and the browser formats to device-local. Must be set before Spring boots.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(TalliApplication.class, args);
	}

}

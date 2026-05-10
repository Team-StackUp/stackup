package com.stackup.stackup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class StackupApplication {

	public static void main(String[] args) {
		SpringApplication.run(StackupApplication.class, args);
	}

}

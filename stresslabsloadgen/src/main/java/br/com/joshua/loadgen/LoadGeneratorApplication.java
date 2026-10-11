package br.com.joshua.loadgen;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class LoadGeneratorApplication {

	public static void main(String[] args) {
		SpringApplication.run(LoadGeneratorApplication.class, args);
	}

}

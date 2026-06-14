package at.hcw.serviceratebackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ServiceRateBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(ServiceRateBackendApplication.class, args);
	}

	// Wird vom LocationValidationService für den Aufruf der Zippopotam.us-API genutzt
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

}

package com.recarry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling   // 예약 요청 만료 (BookingExpiry)
public class RecarryBackendApplication {

	public static void main(String[] args) {
		SpringApplication.run(RecarryBackendApplication.class, args);
	}

}

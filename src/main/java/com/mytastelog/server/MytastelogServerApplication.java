package com.mytastelog.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class MytastelogServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MytastelogServerApplication.class, args);
	}

}

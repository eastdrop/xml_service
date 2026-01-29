package com.xmlservice.xmlservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class XmlserviceApplication {
	public static void main(String[] args) {
		SpringApplication.run(XmlserviceApplication.class, args);
	}
}
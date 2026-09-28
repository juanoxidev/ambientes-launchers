package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PingController {

	@Value("${app.environment:unknown}")
	private String environment;

	@GetMapping("/ping")
	public String ping() {
		return "pong - " + environment;
	}

}

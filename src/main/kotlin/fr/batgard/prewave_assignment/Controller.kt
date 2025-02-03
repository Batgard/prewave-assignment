package fr.batgard.prewave_assignment

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

	@GetMapping("/")
	public fun index(): String {
		return "Greetings from Spring Boot!"
	}

}

package fr.batgard.prewave_assignment

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@SpringBootApplication
class PrewaveAssignmentApplication {
}

fun main(args: Array<String>) {
	runApplication<PrewaveAssignmentApplication>(*args)
}

@Configuration
class AppConfig {
	@Autowired
	private lateinit var environment: org.springframework.core.env.Environment

	@Bean
	fun hostIP(): String = System.getenv("HOST_IP")

	@Bean
	fun port(): String? = environment.getProperty("server.port")
}

package fr.batgard.prewave_assignment

import org.springframework.boot.CommandLineRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean

@SpringBootApplication
class PrewaveAssignmentApplication {
//	@Bean
//	fun commandLineRunner(ctx: ApplicationContext): CommandLineRunner {
//		return CommandLineRunner {
//			println("Let's inspect the beans provided by Spring Boot:")
//			val beanNames = ctx.beanDefinitionNames
//			beanNames.sort()
//			beanNames.onEach {
//				println(it)
//			}
//		}
//	}

}

fun main(args: Array<String>) {
	runApplication<PrewaveAssignmentApplication>(*args)
}

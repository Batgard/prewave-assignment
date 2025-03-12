import org.jooq.codegen.GenerationTool
import org.jooq.meta.jaxb.*
import org.jooq.meta.jaxb.Configuration

plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.4.2"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.jooq.jooq-codegen-gradle") version "3.19.18"
}

group = "fr.batgard"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

repositories {
    mavenCentral()
}

sourceSets {
    main {
        kotlin {
            srcDir("build/generated-src/jooq/main") // Adjust the path to match your JOOQ output directory
        }
    }
}

buildscript {
    dependencies {
        classpath("org.postgresql:postgresql:42.7.5")
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-jooq")
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jooq:jooq:3.19.18")
    implementation("org.jooq:jooq-meta:3.19.18")
    implementation("org.jooq:jooq-codegen:3.19.18")
    implementation("org.jooq:jooq-meta-extensions:3.19.18")
    implementation("org.postgresql:postgresql:42.7.5")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.17")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jooq {
    configuration {

        // Configure the database connection here
        jdbc {
            driver = "org.postgresql.Driver"
            url = "jdbc:postgresql://database:5432/tree_edge_db"
            user = "prewave"
            password = "prew4vePwd"
        }
        generator {
            name = "org.jooq.codegen.KotlinGenerator"
            database {
                name = "org.jooq.meta.postgres.PostgresDatabase"
                inputSchema = "public"
            }
            generate {
                isDaos = true
                isPojosAsKotlinDataClasses = true
            }
            target {
                packageName = "fr.batgard.prewave.db.models"
                directory = "build/generated-src/jooq/main"
            }
        }
    }
}

/**
 * Equivalent to running 'jooq' task?
 */
tasks.register("generateJooq") {
    group = "jooq"
    description = "Generates JOOQ code from the database schema."

    doFirst {
        Class.forName("org.postgresql.Driver")
    }
    doLast {
        val configuration = Configuration()
            .withJdbc(
                Jdbc()
                    .withDriver("org.postgresql.Driver")
                    .withUrl("jdbc:postgresql://database:5432/tree_edge_db")
                    .withUser("prewave")
                    .withPassword("prew4vePwd")
            )
            .withGenerator(
                Generator()
                    .withName("org.jooq.codegen.KotlinGenerator")
                    .withDatabase(
                        Database()
                            .withName("org.jooq.meta.postgres.PostgresDatabase")
                            .withInputSchema("public") // no idea what I should write here
                    )
                    .withGenerate(
                        Generate()
                            .withDaos(true)
                            .withPojos(true)
                    )
                    .withTarget(
                        org.jooq.meta.jaxb.Target()
                            .withPackageName("fr.batgard.prewave_assignment.db.models")
                            .withDirectory("${projectDir}/build/generated-src/jooq/main")
                    )
            )
        GenerationTool.generate(configuration)
    }
}

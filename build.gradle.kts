plugins {
    java
    id("org.springframework.boot") version "3.5.0"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "com.axioma"
version = "0.1.0"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

// 1.21.0 (la que trae Boot 3.5.0) usa una versión de la API de Docker que Docker Engine 29 rechaza,
// y los IT se omitían en silencio.
extra["testcontainers.version"] = "1.21.4"
// google-cloud-storage (libraries-bom 26.90.0) se compila contra estas versiones; Boot 3.5.0 las bajaría
// (OpenTelemetry 1.49, Gson 2.13) y el cliente podría fallar en runtime con NoSuchMethodError.
extra["opentelemetry.version"] = "1.62.0"
extra["gson.version"] = "2.14.0"

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-r2dbc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.postgresql:r2dbc-postgresql")

    // Hash de contraseñas (bcrypt) y validación de ID tokens de Google (API admin)
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.springframework.security:spring-security-oauth2-jose")

    // Logos: Cloud Storage (credenciales por ADC) y lector WebP para ImageIO (el JDK solo trae PNG/JPEG/GIF/BMP)
    implementation(platform("com.google.cloud:libraries-bom:26.90.0"))
    implementation("com.google.cloud:google-cloud-storage")
    // Avisos para el wallet-service (outbox -> Pub/Sub)
    implementation("com.google.cloud:google-cloud-pubsub")
    implementation("com.twelvemonkeys.imageio:imageio-webp:3.15.3")

    // Migraciones (Flyway usa JDBC; la aplicacion usa R2DBC)
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    compileOnly("org.projectlombok:lombok")
    annotationProcessor("org.projectlombok:lombok")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("io.projectreactor:reactor-test")
    testImplementation("org.testcontainers:junit-jupiter")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:gcloud")
    testCompileOnly("org.projectlombok:lombok")
    testAnnotationProcessor("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

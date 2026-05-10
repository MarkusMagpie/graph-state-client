plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    // HTTP клиент Apache HttpClient
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
    // JSON парсер Jackson
    // https://habr.com/ru/companies/otus/articles/687004/
    implementation("com.fasterxml.jackson.core:jackson-databind:2.16.1")
    // логирование
    implementation("org.slf4j:slf4j-simple:2.0.9")
}

tasks.test {
    useJUnitPlatform()
}
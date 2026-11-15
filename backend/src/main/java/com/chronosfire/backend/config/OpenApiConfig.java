package com.chronosfire.backend.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI chronosFireOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Chronos Fire API")
                        .version("v1")
                        .description("API de solo lectura sobre PostgreSQL para el calendario de actividad de fuego. "
                                + "Fuente: NASA FIRMS (MODIS C6.1 y VIIRS SNPP/NOAA-20/NOAA-21).")
                        .contact(new Contact().name("Chronos Fire")))
                .addServersItem(new Server().url("http://127.0.0.1:8080").description("Local"));
    }
}

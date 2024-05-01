package org.example.webrtc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.reactive.config.EnableWebFlux;

@EnableWebFlux
@SpringBootApplication
public class WebrtcApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebrtcApplication.class, args);
    }

}

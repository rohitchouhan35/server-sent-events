package com.sse.serversentevents.controller;

import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

@RestController
@RequestMapping("/server-events")
public class ServerSentEvents {

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> getEvents() throws IOException {

        Stream<String> lines = Files.lines(Path.of("C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/pom.xml"));
        AtomicInteger counter = new AtomicInteger(1);
        return Flux.fromStream(lines)
                .filter(line -> !line.isBlank())
                .map(line -> ServerSentEvent.<String> builder()
                        .id(String.valueOf(counter.getAndIncrement()))
                        .data(line)
                        .event("lineEvent")
                        .retry(Duration.ofMillis(10))
                        .build())
                .delayElements(Duration.ofMillis(300));
    }

    @GetMapping(path = "/alternative", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> getEventsAlternativeOption() throws IOException {

        //TODO: Replace this with your file path
        Stream<String> lines = Files.lines(Path.of("C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/pom.xml"));
        return Flux.fromStream(lines)
                .filter(line -> !line.isBlank())
                .delayElements(Duration.ofMillis(300));
    }

}

package com.sse.serversentevents.service;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import static com.sse.serversentevents.controller.VideoStreamingController.longVideoLink;

@Service
public class VideoStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamingService.class);
    private final String shortDemoVideoPath = "C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/src/main/resources/videos/small_video_for_stream.mp4";

    public Flux<ServerSentEvent<String>> streamSSEVideo() {
        Path videoPath = Path.of(shortDemoVideoPath);
        AtomicInteger counter = new AtomicInteger(0);

        return Flux.defer(() -> {
            try {
                InputStream inputStream = Files.newInputStream(videoPath);
                return Flux.interval(Duration.ofMillis(200))
                        .onBackpressureBuffer(1000)
                        .map(sequence -> {
                            try {
                                byte[] chunk = new byte[1024 * 64];
                                int bytesRead = inputStream.read(chunk);
                                if (bytesRead > 0) {
                                    logger.info("Received chunk {} ({} bytes)", counter.get(), bytesRead);
                                    String encodedChunk = Base64.getEncoder().encodeToString(chunk);
                                    return ServerSentEvent.<String>builder()
                                            .id(String.valueOf(counter.getAndIncrement()))
                                            .data(encodedChunk)
                                            .event("videoEvent")
                                            .retry(Duration.ofMillis(500))
                                            .build();
                                } else {
                                    inputStream.close();
                                    return null;
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                                return null;
                            }
                        })
                        .takeUntil(i -> inputStreamIsClosed(inputStream))
                        .doFinally(signalType -> closeInputStream(inputStream));
            } catch (IOException e) {
                e.printStackTrace();
                return Flux.empty();
            }
        });
    }

    private boolean inputStreamIsClosed(InputStream inputStream) {
        try {
            return inputStream.available() == 0;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    private void closeInputStream(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

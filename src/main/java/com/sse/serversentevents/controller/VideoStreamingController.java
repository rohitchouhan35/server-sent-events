package com.sse.serversentevents.controller;

import com.sse.serversentevents.service.StreamVideoV1;
import com.sse.serversentevents.service.VideoStreamingService;
import org.apache.catalina.connector.ClientAbortException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import reactor.core.publisher.Flux;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/video-stream")
@CrossOrigin("*")
public class VideoStreamingController {

    public static String longVideoLink = "D:/DevOps/video_lectures/React js/React Project Tutorial_ Build a Responsive Portfolio Website w_ Advanced Animations (2022).mp4";
    public static String shortVideoLink = "C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/src/main/resources/videos/small_video_for_stream.mp4";

    @Autowired
    private StreamVideoV1 streamVideoV1;
    @Autowired
    private VideoStreamingService videoStreamingService;

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<byte[]>> streamVideos() {
        return videoStreamingService.streamSSEVideo();
    }

    @GetMapping(value = "/video", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<byte[]>> streamVideo() {
        Path videoPath = Path.of(shortVideoLink);

        return Flux.create(sink -> {
            try (InputStream inputStream = Files.newInputStream(videoPath)) {
                byte[] buffer = new byte[1024 * 256];
                int bytesRead;
                AtomicInteger counter = new AtomicInteger(1);

                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOfRange(buffer, 0, bytesRead);

                    sink.next(ServerSentEvent.<byte[]>builder()
                            .id(String.valueOf(counter.getAndIncrement()))
                            .data(chunk)
                            .event("videoEvent")
                            .retry(Duration.ofMillis(500))
                            .build());
                }
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                sink.complete();
            }
        });
    }


    @GetMapping("/play-video")
    public ResponseEntity<StreamingResponseBody> streamVideoFile(@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        return streamVideoV1.streamVideoFile(rangeHeader);
    }

    @GetMapping("/video2")
    public ResponseEntity<String> streamVideo(SseEmitter sseEmitter) {

        try (FileInputStream videoStream = new FileInputStream(shortVideoLink)) {
            byte[] buffer = new byte[1024];
            int bytesRead;

            while ((bytesRead = videoStream.read(buffer)) > 0) {
                // Create an SSE event with data type "video" and the video data
                String event = "data:video," + Base64.getEncoder().encodeToString(buffer) + "\n\n";

                // Send the event to the client
                sseEmitter.send(event);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return ResponseEntity.ok().build();
    }

}

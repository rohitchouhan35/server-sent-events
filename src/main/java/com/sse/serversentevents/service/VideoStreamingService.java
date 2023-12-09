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
import java.util.concurrent.atomic.AtomicInteger;

import static com.sse.serversentevents.controller.VideoStreamingController.longVideoLink;

@Service
public class VideoStreamingService {

    private static final Logger logger = LoggerFactory.getLogger(VideoStreamingService.class);
    private final String shortDemoVideoPath = "C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/src/main/resources/videos/small_video_for_stream.mp4";

    public Flux<ServerSentEvent<byte[]>> streamSSEVideo() {
        Path videoPath = Path.of(shortDemoVideoPath);
        AtomicInteger counter = new AtomicInteger(0);

        return Flux.defer(() -> {
            try {
                InputStream inputStream = Files.newInputStream(videoPath);
                return Flux.interval(Duration.ofMillis(200))
                        .onBackpressureBuffer(1000)
                        .map(sequence -> {
                            try {
                                byte[] chunk = new byte[100 * 64];
                                int bytesRead = inputStream.read(chunk);
                                if (bytesRead > 0) {
                                    logger.info("Received chunk {} ({} bytes)", counter.get(), bytesRead);
                                    // Uncomment the line below if you want to see the actual content of the chunk
//                                     logger.info("Chunk content: {}", new String(chunk, StandardCharsets.UTF_8));
                                    return ServerSentEvent.<byte[]>builder()
                                            .id(String.valueOf(counter.getAndIncrement()))
                                            .data(chunk)
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

    public ResponseEntity<StreamingResponseBody> getVideoStream2(String rangeHeader) {
        File file = new File(longVideoLink);

        if (!file.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "video/mp4");

        // Check if range header is present
        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            String[] rangeValues = rangeHeader.substring("bytes=".length()).split("-");
            long start = Long.parseLong(rangeValues[0]);
            long end = (rangeValues.length > 1 && !rangeValues[1].isEmpty()) ? Long.parseLong(rangeValues[1]) : file.length() - 1;

            // Set Content-Range header
            headers.add("Content-Range", "bytes " + start + "-" + end + "/" + file.length());

            // Adjust headers and stream based on the requested range
            StreamingResponseBody stream = out -> {
                try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                    randomAccessFile.seek(start);
                    byte[] bytes = new byte[1024 * 64];
                    int length;
                    while ((length = randomAccessFile.read(bytes)) >= 0 && randomAccessFile.getFilePointer() <= end) {
                        try {
                            out.write(bytes, 0, length);
                        } catch (ClientAbortException e) {
                            Thread.sleep(1000);
                            // Client disconnected, ignore the exception
//                            e.printStackTrace();
                            System.out.println("Client disconnected 105");
                            break;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("IO Exception with line 110");
//                    e.printStackTrace();
                }
            };

            return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(stream);
        } else {
            // No range requested, serve the entire video
            headers.add("Content-Length", String.valueOf(file.length()));
            StreamingResponseBody stream = out -> {
                try (InputStream inputStream = new FileInputStream(file)) {
                    byte[] bytes = new byte[1024 * 64];
                    int length;
                    while ((length = inputStream.read(bytes)) >= 0) {
                        try {
                            out.write(bytes, 0, length);
                        } catch (ClientAbortException e) {
                            Thread.sleep(1000);
                            // Client disconnected, ignore the exception
//                            e.printStackTrace();
                            System.out.println("Client disconnected with line 130");
                            break;
                        }
                    }
                } catch (IOException | InterruptedException e) {
                    System.out.println("IO Exception with line 135");
//                    e.printStackTrace();
                }
            };
            return ResponseEntity.ok().headers(headers).body(stream);
        }
    }

    public ResponseEntity<StreamingResponseBody> getVideoStream(String rangeHeader) {
        File videoFile = new File(shortDemoVideoPath);

        if (!videoFile.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "video/mp4");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handlePartialContentRequest(videoFile, rangeHeader, headers);
        } else {
            return handleFullContentRequest(videoFile, headers);
        }
    }

    private ResponseEntity<StreamingResponseBody> handlePartialContentRequest(File videoFile, String rangeHeader, HttpHeaders headers) {
        String[] rangeValues = rangeHeader.substring("bytes=".length()).split("-");
        long start = Long.parseLong(rangeValues[0]);
        long end = (rangeValues.length > 1 && !rangeValues[1].isEmpty()) ? Long.parseLong(rangeValues[1]) : videoFile.length() - 1;

        headers.add("Content-Range", "bytes " + start + "-" + end + "/" + videoFile.length());

        StreamingResponseBody stream = out -> {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(videoFile, "r")) {
                randomAccessFile.seek(start);
                byte[] bytes = new byte[1024 * 64];
                int length;
                while ((length = randomAccessFile.read(bytes)) >= 0 && randomAccessFile.getFilePointer() <= end) {
                    handleStreamWrite( out, bytes, length);
                }
            } catch (IOException | InterruptedException e) {
                handleIOException(e);
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(stream);
    }

    private ResponseEntity<StreamingResponseBody> handleFullContentRequest(File videoFile, HttpHeaders headers) {
        headers.add("Content-Length", String.valueOf(videoFile.length()));

        StreamingResponseBody stream = out -> {
            try (InputStream inputStream = new FileInputStream(videoFile)) {
                byte[] bytes = new byte[1024 * 64];
                int length;
                while ((length = inputStream.read(bytes)) >= 0) {
                    handleStreamWrite(out, bytes, length);
                }
            } catch (IOException | InterruptedException e) {
                handleIOException(e);
            }
        };

        return ResponseEntity.ok().headers(headers).body(stream);
    }

    private void handleStreamWrite(OutputStream out, byte[] bytes, int length) throws IOException, InterruptedException {
        try {
            out.write(bytes, 0, length);
        } catch (ClientAbortException e) {
            Thread.sleep(1000);
            // Client disconnected, ignore the exception
            System.out.println("Client disconnected");
        }
    }

    private void handleIOException(Exception e) {
        System.out.println("IO Exception: " + e.getMessage());
    }

}

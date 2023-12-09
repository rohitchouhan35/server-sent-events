package com.sse.serversentevents.service;

import org.apache.catalina.connector.ClientAbortException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.*;

@Service
public class StreamVideoV1 {

    public static String longVideoLink = "D:/DevOps/video_lectures/React js/React Project Tutorial_ Build a Responsive Portfolio Website w_ Advanced Animations (2022).mp4";
    public static String shortVideoLink = "C:/Users/asus nitro 5/Desktop/Academics/spring-java/server-sent-events/src/main/resources/videos/small_video_for_stream.mp4";

    public ResponseEntity<StreamingResponseBody> streamVideoFile(@RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) {
        File file = new File(longVideoLink);

        if (!file.isFile()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(null);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-Type", "video/mp4");

        if (rangeHeader != null && rangeHeader.startsWith("bytes=")) {
            return handlePartialContentRequest(file, rangeHeader, headers);
        } else {
            return handleFullContentRequest(file, headers);
        }
    }

    private ResponseEntity<StreamingResponseBody> handlePartialContentRequest(File file, String rangeHeader, HttpHeaders headers) {
        String[] rangeValues = rangeHeader.substring("bytes=".length()).split("-");
        long start = Long.parseLong(rangeValues[0]);
        long end = (rangeValues.length > 1 && !rangeValues[1].isEmpty()) ? Long.parseLong(rangeValues[1]) : file.length() - 1;

        headers.add("Content-Range", "bytes " + start + "-" + end + "/" + file.length());

        StreamingResponseBody stream = out -> {
            try (RandomAccessFile randomAccessFile = new RandomAccessFile(file, "r")) {
                randomAccessFile.seek(start);
                byte[] bytes = new byte[1024 * 64];
                int length;
                while ((length = randomAccessFile.read(bytes)) >= 0 && randomAccessFile.getFilePointer() <= end) {
                    handleStreamWrite(out, bytes, length);
                }
            } catch (IOException | InterruptedException e) {
                handleIOException(e);
            }
        };

        return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).body(stream);
    }

    private ResponseEntity<StreamingResponseBody> handleFullContentRequest(File file, HttpHeaders headers) {
        headers.add("Content-Length", String.valueOf(file.length()));

        StreamingResponseBody stream = out -> {
            try (InputStream inputStream = new FileInputStream(file)) {
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

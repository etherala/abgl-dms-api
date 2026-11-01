package com.abgl.dms.documentupload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class ClamAvScanner {

    private static final int CHUNK_SIZE = 64 * 1024;
    private static final int MAX_RESPONSE_BYTES = 1024;

    private final String host;
    private final int port;
    private final int timeoutMillis;

    public ClamAvScanner(
            @Value("${file.upload.clamav.host:127.0.0.1}") String host,
            @Value("${file.upload.clamav.port:3310}") int port,
            @Value("${file.upload.clamav.timeout-ms:30000}") int timeoutMillis) {
        this.host = host;
        this.port = port;
        this.timeoutMillis = timeoutMillis;
    }

    public void scan(Path file) throws IOException {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), timeoutMillis);
            socket.setSoTimeout(timeoutMillis);

            try (DataOutputStream output = new DataOutputStream(
                    new BufferedOutputStream(socket.getOutputStream()));
                 BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
                 var fileInput = Files.newInputStream(file)) {
                output.write(new byte[]{'z', 'I', 'N', 'S', 'T', 'R', 'E', 'A', 'M', 0});
                byte[] buffer = new byte[CHUNK_SIZE];
                int bytesRead;
                while ((bytesRead = fileInput.read(buffer)) != -1) {
                    output.writeInt(bytesRead);
                    output.write(buffer, 0, bytesRead);
                }
                output.writeInt(0);
                output.flush();

                String response = readResponse(input);
                if ("OK".equals(response) || response.endsWith(": OK")) {
                    return;
                }
                if (response.endsWith(": FOUND")) {
                    throw new InfectedFileException("Malware was detected in the uploaded file");
                }
                throw new IOException("ClamAV returned an unsuccessful scan response");
            }
        }
    }

    private String readResponse(BufferedInputStream input) throws IOException {
        StringBuilder response = new StringBuilder();
        int nextByte;
        while ((nextByte = input.read()) != -1 && nextByte != 0) {
            if (response.length() >= MAX_RESPONSE_BYTES) {
                throw new IOException("ClamAV response exceeded the expected length");
            }
            response.append((char) nextByte);
        }
        if (nextByte == -1) {
            throw new IOException("ClamAV closed the connection without a scan result");
        }
        return response.toString().trim();
    }

    public static class InfectedFileException extends IOException {
        public InfectedFileException(String message) {
            super(message);
        }
    }
}

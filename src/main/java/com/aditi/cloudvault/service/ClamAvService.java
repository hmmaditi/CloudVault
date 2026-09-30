package com.aditi.cloudvault.service;


import org.springframework.stereotype.Service;

import java.io.DataOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

@Service
public class ClamAvService {

    private static final String CLAMAV_HOST = "127.0.0.1";
    private static final int CLAMAV_PORT = 3310;
    
    public boolean isFileSafe(byte[] fileBytes) {
        try (Socket socket = new Socket(CLAMAV_HOST, CLAMAV_PORT);
             DataOutputStream output =
                     new DataOutputStream(socket.getOutputStream());
             InputStream input = socket.getInputStream()) {

        // Tell ClamAV that we are sending a stream of file data
        output.write("zINSTREAM\0".getBytes(StandardCharsets.UTF_8));

        // Send file in chunks
        int offset = 0;
        int chunkSize = 2048;

        while(offset < fileBytes.length) {
            int length = Math.min(chunkSize, fileBytes.length - offset);

            output.writeInt(length);
            output.write(fileBytes, offset, length);
            offset += length;
        }

        output.writeInt(0);
        output.flush();

            byte[] responseBytes = input.readAllBytes();

            String response =
                    new String(responseBytes, StandardCharsets.UTF_8);

            System.out.println("ClamAV response: " + response);

            return response.contains("OK");


        } catch (Exception e) {
            e.printStackTrace();

            throw new IllegalStateException(
                    "Could not connect to ClamAV",
                    e
            );
        }
    }
}

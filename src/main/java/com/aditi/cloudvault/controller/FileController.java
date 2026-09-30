package com.aditi.cloudvault.controller;

import com.aditi.cloudvault.dto.FileResponse;
import com.aditi.cloudvault.dto.PresignedUploadRequest;
import com.aditi.cloudvault.dto.UploadResponse;
import com.aditi.cloudvault.dto.UploadUrlResponse;
import com.aditi.cloudvault.model.User;
import com.aditi.cloudvault.service.AuthService;
import com.aditi.cloudvault.service.FileService;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.util.List;

@RestController

public class FileController {
    private final FileService fileService;
    private final AuthService authService;

    public FileController(
            FileService fileService,
            AuthService authService) {

        this.fileService = fileService;
        this.authService = authService;
    }

    @PostMapping("/upload")
        public ResponseEntity<UploadResponse> uploadFile(
                @RequestParam("file") MultipartFile file) throws IOException{


            System.out.println("!!! UPLOAD HIT !!!");   // ye line add karo


            UploadResponse response = fileService.uploadFile(file);

            return ResponseEntity.ok(response);
        }


    @GetMapping("/files")
    public List<FileResponse> getAllFiles() {

        User currentUser = authService.getCurrentUser();

        return fileService.getAllFiles(
                currentUser.getId()
        );
    }

    @DeleteMapping("/files/{fileId}")
    public ResponseEntity<String> deleteFile(@PathVariable Long fileId) {
        User currentUser = authService.getCurrentUser();
        fileService.deleteOwnedFile(
                fileId,
                currentUser.getId()
        );

            return ResponseEntity.ok("file deleted successfully");
    }

    @GetMapping("/files/{fileId}/download")
    public ResponseEntity<byte[]> downloadFile(
            @PathVariable Long fileId) throws IOException {

        User currentUser = authService.getCurrentUser();

        ResponseBytes<GetObjectResponse> response =
                fileService.downloadOwnedFile(
                        fileId,
                        currentUser.getId()
                );

        return ResponseEntity.ok()
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"file-" + fileId + "\""
                )
                .contentType(
                        MediaType.parseMediaType(
                                response.response().contentType()
                        )
                )
                .body(response.asByteArray());
    }



    @GetMapping("/files/{fileId}/download-url")
    public ResponseEntity<String> generateDownloadUrl(
            @PathVariable Long fileId) {

        String url = fileService.generateDownloadUrl(fileId);
        return ResponseEntity.ok(url);
    }


    @PostMapping("/upload-url")
        public UploadUrlResponse generateUploadUrl(
            @RequestBody PresignedUploadRequest request) {

            return fileService.generateUploadUrl(request);
        }


    @PostMapping("/{fileId}/quarantine")
    public void markAsQuarantined(@PathVariable Long fileId) {

        User currentUser = authService.getCurrentUser();

        fileService.markAsQuarantined(
                fileId,
                currentUser.getId()
        );

    }

    @PostMapping("/{fileId}/scan")
    public void calculateAndStoreHash(@PathVariable Long fileId) {

        User currentUser = authService.getCurrentUser();

        fileService.calculateAndStoreHash(
                fileId,
                currentUser.getId()
        );
    }
    }





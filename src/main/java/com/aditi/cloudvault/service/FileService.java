package com.aditi.cloudvault.service;

import com.aditi.cloudvault.dto.FileResponse;
import com.aditi.cloudvault.dto.PresignedUploadRequest;
import com.aditi.cloudvault.dto.UploadResponse;
import com.aditi.cloudvault.dto.UploadUrlResponse;
import com.aditi.cloudvault.enums.FileStatus;
import com.aditi.cloudvault.exception.FileAccessDeniedException;
import com.aditi.cloudvault.exception.RateLimitExceededException;
import com.aditi.cloudvault.model.FileMetadata;
import com.aditi.cloudvault.repository.FileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import com.aditi.cloudvault.model.User;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;


import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;




@Service
public class FileService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "image/png",
            "image/jpeg",
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "text/plain"
    ); //file validation 3

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final FileRepository fileRepository;
    private final AuthService authService;
    private final ClamAvService clamAvService;
    private final AuditLogService auditLogService;
    private final RateLimitService rateLimitService;

    @Value("${aws.bucketName}")
    private String bucketName;

    @Value("${cloudvault.file.max-size}")
    private long maxFileSize;

    private static final Logger log =
            LoggerFactory.getLogger(FileService.class);

    public FileService(
            S3Client s3Client,
            S3Presigner s3Presigner,
            FileRepository fileRepository,
            AuthService authService,
            ClamAvService clamAvService,
            AuditLogService auditLogService,
            RateLimitService rateLimitService
    ) {

        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.fileRepository = fileRepository;
        this.authService = authService;
        this.clamAvService = clamAvService;
        this.auditLogService = auditLogService;
        this.rateLimitService = rateLimitService;
    }

    public UploadResponse uploadFile(MultipartFile file) throws IOException {
        {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("File cannot be empty");
            } //file validation

            long maxSize = 10 * 1024 * 1024; // 10 MB

            if (file.getSize() > maxSize) {
                throw new IllegalArgumentException("File size cannot exceed 10 MB");
            } //file validation 2

            String contentType = file.getContentType();
            byte[] fileBytes = file.getBytes();
            if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
                throw new IllegalArgumentException("File type not allowed");
            }  //file validation 3 mime validation

            if (!hasValidFileSignature(fileBytes, contentType)) {
                throw new IllegalArgumentException("file content does not match its declared type");
            } //MAGIC BYTE


            String originalFileName = file.getOriginalFilename();

            String safeFileName = sanitizeFileName(originalFileName);

            String fileHash = calculateSHA256(file.getBytes());

            System.out.println("SHA-256: " + fileHash);

            String fileName = UUID.randomUUID() + "_" + safeFileName;


            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .serverSideEncryption(ServerSideEncryption.AES256)
                    .build();

            s3Client.putObject(
                    request,
                    RequestBody.fromBytes(file.getBytes())
            );

            User currentUser = authService.getCurrentUser();
            FileMetadata metadata = new FileMetadata();

            metadata.setOriginalFileName(originalFileName);
            metadata.setS3Key(fileName);
            metadata.setSha256(fileHash);
            metadata.setFileSize(file.getSize());
            metadata.setContentType(contentType);
            metadata.setUploadedAt(LocalDateTime.now());
            metadata.setStatus(FileStatus.VERIFIED);
            metadata.setOwner(currentUser);
            System.out.println(">>> ABOUT TO SAVE METADATA");
            fileRepository.save(metadata);
            System.out.println(">>> METADATA SAVED: " + metadata.getId());


            fileRepository.save(metadata);

            String fileUrl = "https://" + bucketName + ".s3.amazonaws.com/" + fileName;

            return new UploadResponse(
                    "File uploaded successfully",
                    fileName,
                    fileUrl
            );


        }


    }


    public List<FileResponse> getAllFiles() {

        ListObjectsV2Request request = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .build();


        ListObjectsV2Response response = s3Client.listObjectsV2(request);

        List<FileResponse> files = new ArrayList<>();

        for (S3Object object : response.contents()) {

            String fileName = object.key();

            String fileUrl = "https://" + bucketName + ".s3.amazonaws.com/" + fileName;

            files.add(new FileResponse(fileName, fileUrl));
        }

        return files;
    }

    public List<FileResponse> getAllFiles(Long ownerId) {

        List<FileMetadata> files =
                fileRepository.findByOwnerId(ownerId);

        List<FileResponse> result = new ArrayList<>();

        for (FileMetadata metadata : files) {


            String fileUrl =
                    generateDownloadUrl(metadata.getId());

            result.add(new FileResponse(
                    metadata.getOriginalFileName(),
                    fileUrl
            ));
        }

        return result;
    }

    public void deleteFile(String fileName) {

        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        s3Client.headObject(headRequest);

        DeleteObjectRequest deleteRequest = DeleteObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        s3Client.deleteObject(deleteRequest);


    }

    public void deleteOwnedFile(Long fileId, Long ownerId) {

        FileMetadata metadata = fileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() ->

                        new FileAccessDeniedException("You do not own this file"));

        deleteFile(metadata.getS3Key());

        log.info(
                "event=FILE_DELETED userId={} fileId={} result=SUCCESS",
                ownerId,
                fileId
        );

        auditLogService.logEvent(
                ownerId,
                fileId,
                "FILE_DELETED",
                "SUCCESS",
                "USER_REQUESTED_DELETE"
        );

    }

    public ResponseBytes<GetObjectResponse> downloadFile(String fileName) {

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(fileName)
                .build();

        ResponseBytes<GetObjectResponse> response =
                s3Client.getObjectAsBytes(request);

        return response;
    }

    public String generateDownloadUrl(Long fileId) {

        User currentUser = authService.getCurrentUser();

        if (rateLimitService.isBlocked(
                currentUser.getId().toString(),
                "DOWNLOAD")) {

            throw new RateLimitExceededException(
                    "Too many download requests. Try again later."
            );
        }

        FileMetadata metadata = fileRepository
                .findByIdAndOwnerId(fileId, currentUser.getId())
                .orElseThrow(() -> {

                    log.warn(
                            "event=DOWNLOAD_DENIED userId={} fileId={} reason={}",
                            currentUser.getId(),
                            fileId,
                            "USER_DOES_NOT_OWN_FILE"
                    );

                    auditLogService.logEvent(
                            currentUser.getId(),
                            fileId,
                            "Download denied",
                            "Failure",
                            "User_does_not_own_file"

                    );
                    return new FileAccessDeniedException(
                            "You do not own this file");

                });


        if (metadata.getStatus() != FileStatus.VERIFIED) {

            auditLogService.logEvent(
                    currentUser.getId(),
                    fileId,
                    "DOWNLOAD_DENIED",
                    "FAILURE",
                    "FILE_NOT_VERIFIED"
            );

            throw new IllegalStateException(
                    "File not available for download");
        }


        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(metadata.getS3Key())
                .build();


        GetObjectPresignRequest getObjectPresignRequest =
                GetObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(10))
                        .getObjectRequest(getObjectRequest)
                        .build();

        PresignedGetObjectRequest presignedRequest =
                s3Presigner.presignGetObject(getObjectPresignRequest);

        rateLimitService.recordAttempt(
                currentUser.getId().toString(),
                "DOWNLOAD"
        );

        rateLimitService.checkAndBlock(
                currentUser.getId().toString(),
                "DOWNLOAD"
        );

        log.info(
                "event=DOWNLOAD_ALLOWED userId={} fileId={} result=SUCCESS",
                currentUser.getId(),
                fileId
        );

        auditLogService.logEvent(
                currentUser.getId(),
                fileId,
                "DOWNLOAD_ALLOWED",
                "SUCCESS",
                "FILE_VERIFIED"
        );


        return presignedRequest.url().toString();


    }

    private String calculateSHA256(byte[] fileBytes) {

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(fileBytes);

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }

    }

    private boolean hasValidFileSignature(byte[] fileBytes, String contentType) {

        try {

            if (contentType.equals("image/png")) {
                return fileBytes.length >= 8
                        && fileBytes[0] == (byte) 0x89
                        && fileBytes[1] == (byte) 0x50
                        && fileBytes[2] == (byte) 0x4E
                        && fileBytes[3] == (byte) 0x47;
            }

            if (contentType.equals("image/jpeg")) {
                return fileBytes.length >= 3
                        && fileBytes[0] == (byte) 0xFF
                        && fileBytes[1] == (byte) 0xD8
                        && fileBytes[2] == (byte) 0xFF;
            }

            if (contentType.equals("application/pdf")) {
                return fileBytes.length >= 4
                        && fileBytes[0] == '%'
                        && fileBytes[1] == 'P'
                        && fileBytes[2] == 'D'
                        && fileBytes[3] == 'F';
            }

            if (contentType.equals(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document")) {

                try (ZipInputStream zipInputStream =
                             new ZipInputStream(
                                     new ByteArrayInputStream(fileBytes))) {

                    boolean hasContentTypes = false;
                    boolean hasWordDocument = false;

                    ZipEntry entry;

                    while ((entry = zipInputStream.getNextEntry()) != null) {

                        if (entry.getName().equals("[Content_Types].xml")) {
                            hasContentTypes = true;
                        }

                        if (entry.getName().equals("word/document.xml")) {
                            hasWordDocument = true;
                        }
                    }

                    return hasContentTypes && hasWordDocument;
                }
            }

            return true;

        } catch (Exception e) {
            throw new IllegalStateException(
                    "File content does not match its declared type", e);
        }
    }

    private String sanitizeFileName(String originalFileName) {

        if (originalFileName == null || originalFileName.isBlank()) {
            return "unnamed-file";
        }

        String fileName = originalFileName
                .replace("\\", "_")
                .replace("/", "_");

        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");

        return fileName;

    }

    public ResponseBytes<GetObjectResponse> downloadOwnedFile(
            Long fileId,
            Long ownerId) {

        FileMetadata metadata = fileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() -> new FileAccessDeniedException("You do not own this file"));
        return downloadFile(metadata.getS3Key());


    }

    public UploadUrlResponse generateUploadUrl(PresignedUploadRequest request) {
        long maxSize = 10 * 1024 * 1024;

        if (request.getFileSize() <= 0 || request.getFileSize() > maxSize) {
            throw new IllegalArgumentException("File size must be between 1 byte and 10 MB");
        }

        if (request.getContentType() == null || !ALLOWED_TYPES.contains(request.getContentType())) {
            throw new IllegalArgumentException("file type not allowed");
        }

        User currentUser = authService.getCurrentUser();

        if (rateLimitService.isBlocked(
                currentUser.getId().toString(),
                "UPLOAD"))

        {

            throw new RateLimitExceededException(
                    "Too many upload requests. Try again later."
            );


        }

        String safeFilename = sanitizeFileName(request.getFileName());

        String s3Key = "quarantine/"
                + UUID.randomUUID()
                + "/"
                + safeFilename;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .contentType(request.getContentType())
                .serverSideEncryption(ServerSideEncryption.AES256)
                .build();


        PutObjectPresignRequest presignRequest =
                PutObjectPresignRequest.builder()
                        .signatureDuration(Duration.ofMinutes(5))
                        .putObjectRequest(putObjectRequest)
                        .build();

        PresignedPutObjectRequest presignedRequest =
                s3Presigner.presignPutObject(presignRequest);

        rateLimitService.recordAttempt(
                currentUser.getId().toString(),
                "UPLOAD"
        );

        rateLimitService.checkAndBlock(
                currentUser.getId().toString(),
                "UPLOAD"
        );


        log.info(
                "event=FILE_UPLOAD_REQUESTED userId={} fileName={} result=REQUESTED",
                currentUser.getId(),
                safeFilename
        );

        auditLogService.logEvent(
                currentUser.getId(),
                null,
                "FILE_UPLOAD_REQUESTED",
                "REQUESTED",
                "PRESIGNED_UPLOAD_URL_GENERATED"
        );

        FileMetadata metadata = new FileMetadata();

        metadata.setOriginalFileName(request.getFileName());
        metadata.setS3Key(s3Key);
        metadata.getId();
        metadata.setFileSize(request.getFileSize());
        metadata.setContentType(request.getContentType());
        metadata.setUploadedAt(LocalDateTime.now());
        metadata.setStatus(FileStatus.UPLOADING);
        metadata.setOwner(currentUser);

        fileRepository.saveAndFlush(metadata);





        // 8. Send URL back to client
        return new UploadUrlResponse(
                metadata.getId(),
                presignedRequest.url().toString(),
                s3Key
        );


    }

    public void markAsQuarantined(Long fileId, Long ownerId) {

        FileMetadata metadata = fileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() ->

                        new FileAccessDeniedException("You do not own this file"));

        if (metadata.getStatus() != FileStatus.UPLOADING) {
            throw new IllegalStateException("File is not in uploading state");
        }


        HeadObjectRequest headRequest = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(metadata.getS3Key())
                .build();

        HeadObjectResponse response = s3Client.headObject(headRequest);
        long actualSize = response.contentLength();

        if (actualSize > maxFileSize || actualSize != metadata.getFileSize()) {

            log.warn(
                    "event=FILE_BLOCKED userId={} fileId={} reason={}",
                    ownerId,
                    fileId,
                    "FILE_SIZE_VIOLATION"
            );

            auditLogService.logEvent(
                    ownerId,
                    fileId,
                    "FILE_BLOCKED",
                    "BLOCKED",
                    "FILE_SIZE_VIOLATION"
            );

            metadata.setStatus(FileStatus.BLOCKED);
            fileRepository.save(metadata);
            return;
        }


        s3Client.headObject(headRequest);


        metadata.setStatus(FileStatus.QUARANTINED);

        fileRepository.save(metadata);
    }

    public void calculateAndStoreHash(Long fileId, Long ownerId) {

        FileMetadata metadata = fileRepository
                .findByIdAndOwnerId(fileId, ownerId)
                .orElseThrow(() ->
                        new FileAccessDeniedException("You do not own this file"));

        if (metadata.getStatus() != FileStatus.QUARANTINED) {
            throw new IllegalStateException(
                    "File must be quarantined before scanning");
        }

        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(metadata.getS3Key())
                .build();

        ResponseBytes<GetObjectResponse> response =
                s3Client.getObjectAsBytes(request);

        byte[] fileBytes = response.asByteArray();

        boolean validSignature = hasValidFileSignature(fileBytes, metadata.getContentType());


        if (!validSignature) {


            if (!validSignature) {

                log.warn(
                        "event=FILE_BLOCKED userId={} fileId={} reason={}",
                        ownerId,
                        fileId,
                        "MIME_MISMATCH"
                );

                auditLogService.logEvent(
                        ownerId,
                        fileId,
                        "FILE_BLOCKED",
                        "BLOCKED",
                        "MIME_MISMATCH"
                );

                metadata.setStatus(FileStatus.BLOCKED);
                fileRepository.save(metadata);
                return;
            }

            String sha256 = calculateSHA256(fileBytes);

            metadata.setSha256(sha256);

            metadata.setStatus(FileStatus.SCANNING);

            fileRepository.save(metadata);

            boolean isSafe = clamAvService.isFileSafe(fileBytes);

            if (isSafe) {
                metadata.setStatus(FileStatus.VERIFIED);
            } else {
                log.warn(
                        "event=FILE_BLOCKED userId={} fileId={} reason={}",
                        ownerId,
                        fileId,
                        "MALWARE_DETECTED"
                );

                auditLogService.logEvent(
                        ownerId,
                        fileId,
                        "FILE_BLOCKED",
                        "BLOCKED",
                        "MALWARE_DETECTED"
                );

                metadata.setStatus(FileStatus.BLOCKED);
            }

            fileRepository.save(metadata);
        }


    }
}


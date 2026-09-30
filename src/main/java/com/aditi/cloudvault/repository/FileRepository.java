package com.aditi.cloudvault.repository;

import com.aditi.cloudvault.model.FileMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface FileRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByIdAndOwnerId(Long fileId, Long ownerId);
    Optional<FileMetadata> findByS3KeyAndOwnerId(String s3Key, Long ownerId);

    List<FileMetadata> findByOwnerId(Long ownerId);



}

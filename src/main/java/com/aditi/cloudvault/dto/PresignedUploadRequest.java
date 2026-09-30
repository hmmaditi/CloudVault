package com.aditi.cloudvault.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PresignedUploadRequest {

    private String fileName;
    private String contentType;
    private Long fileSize;

}

package com.aditi.cloudvault.dto;


import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UploadUrlResponse {

    private Long fileId;
    private String UploadUrl;
    private String s3Key;

    public UploadUrlResponse(Long fileId, String UploadUrl, String s3Key){
        this.UploadUrl = UploadUrl;
        this.fileId = fileId;
        this.s3Key = s3Key;
    }



}

package com.aditi.cloudvault.dto;


import lombok.Getter;

@Getter
public class FileResponse {

    private String fileName;
    private String fileUrl;

    public FileResponse(String fileName, String fileUrl) {
        this.fileName = fileName;
        this.fileUrl = fileUrl;
    }

}
package com.aditi.cloudvault.dto;

public class UploadResponse {

        private String message;
        private String fileName;
        private String fileUrl;

        public UploadResponse(String message, String fileName, String fileUrl) {
            this.message = message;
            this.fileName = fileName;
            this.fileUrl = fileUrl;
        }

        public String getMessage() {
            return message;
        }

        public String getFileName() {
            return fileName;
        }

        public String getFileUrl() {
            return fileUrl;
        }
    }


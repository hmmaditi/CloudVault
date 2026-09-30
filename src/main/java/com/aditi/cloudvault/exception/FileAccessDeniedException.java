package com.aditi.cloudvault.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.File;

@ResponseStatus(HttpStatus.FORBIDDEN)

public class FileAccessDeniedException extends RuntimeException{

    public FileAccessDeniedException(String message) {
        super(message);
    }



}

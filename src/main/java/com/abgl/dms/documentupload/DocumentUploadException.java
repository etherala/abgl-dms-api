package com.abgl.dms.documentupload;

public class DocumentUploadException extends RuntimeException {

    private final String status;
    private final String publicMessage;

    public DocumentUploadException(String status, String publicMessage) {
        super(publicMessage);
        this.status = status;
        this.publicMessage = publicMessage;
    }

    public DocumentUploadException(String status, String publicMessage, Throwable cause) {
        super(publicMessage, cause);
        this.status = status;
        this.publicMessage = publicMessage;
    }

    public String status() {
        return status;
    }

    public String publicMessage() {
        return publicMessage;
    }
}

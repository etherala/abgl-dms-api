package com.abgl.dms.documentupload;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipFile;

@Component
public class DocumentFileValidator {

    private static final Map<String, String> EXPECTED_MEDIA_TYPES = Map.ofEntries(
            Map.entry("pdf", "application/pdf"),
            Map.entry("doc", "application/msword"),
            Map.entry("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
            Map.entry("xls", "application/vnd.ms-excel"),
            Map.entry("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
            Map.entry("ppt", "application/vnd.ms-powerpoint"),
            Map.entry("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            Map.entry("txt", "text/plain"),
            Map.entry("jpg", "image/jpeg"),
            Map.entry("jpeg", "image/jpeg"),
            Map.entry("png", "image/png"),
            Map.entry("gif", "image/gif")
    );

    private final Set<String> allowedTypes;
    private final Set<String> notAllowedTypes;
    private final long maxFileSizeBytes;

    public DocumentFileValidator(
            @org.springframework.beans.factory.annotation.Value("${file.upload.allowed-types}") String allowedTypes,
            @org.springframework.beans.factory.annotation.Value("${file.upload.not-allowed-types}") String notAllowedTypes,
            @org.springframework.beans.factory.annotation.Value("${spring.servlet.multipart.max-file-size:10MB}")
            org.springframework.util.unit.DataSize maxFileSize) {
        this.allowedTypes = csvSet(allowedTypes);
        this.notAllowedTypes = csvSet(notAllowedTypes);
        this.maxFileSizeBytes = maxFileSize.toBytes();
        if (maxFileSizeBytes < 1) {
            throw new IllegalArgumentException("Upload file-size limit must be positive");
        }
    }

    public String validateAndGetExtension(MultipartFile file) {
        if (file.isEmpty() || file.getSize() == 0) {
            throw rejected("Empty files are not allowed");
        }
        if (file.getSize() > maxFileSizeBytes) {
            throw new DocumentUploadException("REJECTED", "File exceeds the configured size limit");
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw rejected("A filename with an allowed extension is required");
        }

        String normalizedFilename = originalFilename.replace('\\', '/');
        String filename = normalizedFilename.substring(normalizedFilename.lastIndexOf('/') + 1);
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) {
            throw rejected("File extension is missing");
        }
        String extension = filename.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (notAllowedTypes.contains(extension) || !allowedTypes.contains(extension)) {
            throw rejected("File type is not allowed");
        }
        String claimedContentType = file.getContentType();
        String normalizedClaimedType = claimedContentType == null
                ? ""
                : claimedContentType.split(";", 2)[0].trim();
        if (!normalizedClaimedType.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(normalizedClaimedType)) {
            String expectedType = EXPECTED_MEDIA_TYPES.get(extension);
            if (expectedType == null || !expectedType.equalsIgnoreCase(normalizedClaimedType)) {
                throw rejected("Claimed content type does not match the file extension");
            }
        }
        return extension;
    }

    public void validateSignature(Path file, String extension) throws IOException {
        byte[] header = new byte[16];
        byte[] signature;
        try (var input = java.nio.file.Files.newInputStream(file)) {
            signature = input.readNBytes(header.length);
        }
        int headerLength = signature.length;
        System.arraycopy(signature, 0, header, 0, headerLength);
        if (headerLength < 1) {
            throw rejected("Empty files are not allowed");
        }

        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, headerLength, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "png" -> startsWith(header, headerLength,
                    new byte[]{(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10});
            case "jpg", "jpeg" -> headerLength >= 3
                    && (header[0] & 0xff) == 0xff && (header[1] & 0xff) == 0xd8
                    && (header[2] & 0xff) == 0xff;
            case "gif" -> startsWith(header, headerLength, "GIF8".getBytes(StandardCharsets.US_ASCII));
            case "doc", "xls", "ppt" -> startsWith(header, headerLength,
                    new byte[]{(byte) 0xd0, (byte) 0xcf, 0x11, (byte) 0xe0, (byte) 0xa1, (byte) 0xb1, 0x1a, (byte) 0xe1});
            case "docx" -> isOfficeZip(file, "word/document.xml");
            case "xlsx" -> isOfficeZip(file, "xl/workbook.xml");
            case "pptx" -> isOfficeZip(file, "ppt/presentation.xml");
            case "txt" -> isText(file);
            default -> false;
        };
        if (!valid) {
            throw rejected("File signature does not match its claimed extension");
        }
    }

    private boolean isOfficeZip(Path file, String requiredEntry) throws IOException {
        try (ZipFile zip = new ZipFile(file.toFile())) {
            return zip.getEntry("[Content_Types].xml") != null && zip.getEntry(requiredEntry) != null;
        } catch (java.util.zip.ZipException exception) {
            return false;
        }
    }

    private boolean isText(Path file) throws IOException {
        byte[] bytes = java.nio.file.Files.readAllBytes(file);
        for (byte value : bytes) {
            if (value == 0) {
                return false;
            }
        }
        try {
            StandardCharsets.UTF_8.newDecoder().decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException exception) {
            return false;
        }
    }

    private boolean startsWith(byte[] content, int contentLength, byte[] signature) {
        if (contentLength < signature.length) {
            return false;
        }
        for (int index = 0; index < signature.length; index++) {
            if (content[index] != signature[index]) {
                return false;
            }
        }
        return true;
    }

    private Set<String> csvSet(String configuredValue) {
        return java.util.Arrays.stream(configuredValue.split(","))
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .filter(value -> !value.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    private DocumentUploadException rejected(String message) {
        return new DocumentUploadException("REJECTED", message);
    }
}

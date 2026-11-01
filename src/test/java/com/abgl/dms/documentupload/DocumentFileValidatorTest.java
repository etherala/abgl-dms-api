package com.abgl.dms.documentupload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.unit.DataSize;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentFileValidatorTest {

    private static final String ALLOWED_TYPES = "pdf,doc,docx,xls,xlsx,ppt,pptx,txt,jpg,jpeg,png,gif";
    private static final String BLOCKED_TYPES = "exe,dll,bat.cmd,ps1,js,html,htm,svg,php,sh,jar,war,py,ruby,pl";

    private final DocumentFileValidator validator = new DocumentFileValidator(
            ALLOWED_TYPES, BLOCKED_TYPES, DataSize.ofMegabytes(10));

    @Test
    void acceptsPdfWhoseClaimedTypeAndSignatureMatch(@TempDir Path tempDir) throws Exception {
        byte[] pdf = "%PDF-1.7\nbody\n%%EOF".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        MockMultipartFile upload = new MockMultipartFile(
                "file-0", "certificate.pdf", "application/pdf", pdf);
        Path quarantinedFile = tempDir.resolve("upload.quarantine");
        Files.write(quarantinedFile, pdf);

        assertEquals("pdf", validator.validateAndGetExtension(upload));
        validator.validateSignature(quarantinedFile, "pdf");
    }

    @Test
    void rejectsPdfWithMismatchedSignature(@TempDir Path tempDir) throws Exception {
        MockMultipartFile upload = new MockMultipartFile(
                "file-0", "certificate.pdf", "application/pdf", "not a pdf".getBytes());
        Path quarantinedFile = tempDir.resolve("upload.quarantine");
        Files.writeString(quarantinedFile, "not a pdf");

        assertEquals("pdf", validator.validateAndGetExtension(upload));
        assertThrows(DocumentUploadException.class,
                () -> validator.validateSignature(quarantinedFile, "pdf"));
    }

    @Test
    void acceptsDocxWithExpectedOfficePackageEntry(@TempDir Path tempDir) throws Exception {
        byte[] docx = officePackage("word/document.xml");
        MockMultipartFile upload = new MockMultipartFile(
                "file-0",
                "certificate.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docx);
        Path quarantinedFile = tempDir.resolve("upload.quarantine");
        Files.write(quarantinedFile, docx);

        assertEquals("docx", validator.validateAndGetExtension(upload));
        validator.validateSignature(quarantinedFile, "docx");
    }

    @Test
    void rejectsBlockedExtension() {
        MockMultipartFile upload = new MockMultipartFile(
                "file-0", "payload.exe", "application/octet-stream", new byte[]{1});

        assertThrows(DocumentUploadException.class, () -> validator.validateAndGetExtension(upload));
    }

    private byte[] officePackage(String documentEntry) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("[Content_Types].xml"));
            zip.write("<Types/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry(documentEntry));
            zip.write("<document/>".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }
}

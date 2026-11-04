package com.abgl.dms.documentsearch;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;

public record DocumentFileResponse(
        Resource resource,
        String fileName,
        MediaType mediaType
) {}

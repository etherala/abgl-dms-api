package com.abgl.dms.documentupload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record DocumentArchiveRequest(
        @NotNull @Positive Long imageId,
        @NotNull @Positive Integer version
) {}

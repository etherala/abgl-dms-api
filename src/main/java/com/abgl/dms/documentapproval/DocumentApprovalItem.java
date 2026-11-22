package com.abgl.dms.documentapproval;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record DocumentApprovalItem(
        @NotNull @Positive Long imageId,
        @NotNull @Positive Integer version,
        @Size(max = 2000) String remarks
) {}

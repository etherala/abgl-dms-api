package com.abgl.dms.documentapproval;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record DocumentApprovalRequest(
        @NotBlank String action,
        @NotEmpty List<@NotNull @Valid DocumentApprovalItem> documents
) {}

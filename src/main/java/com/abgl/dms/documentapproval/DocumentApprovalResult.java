package com.abgl.dms.documentapproval;

public record DocumentApprovalResult(
        long imageId,
        int version,
        String approvalStatus,
        String message
) {}

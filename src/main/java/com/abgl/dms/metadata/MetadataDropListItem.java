package com.abgl.dms.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;

public record MetadataDropListItem(
        @JsonProperty("md_code") String code,
        @JsonProperty("md_desc") String description
) {}

package com.abgl.dms.cache;

import java.util.Map;

public record MetadataDropList(Map<String, Map<String, String>> valuesByType) {

    public MetadataDropList {
        valuesByType = valuesByType.entrySet().stream()
                .collect(java.util.stream.Collectors.toUnmodifiableMap(
                        Map.Entry::getKey,
                        entry -> Map.copyOf(entry.getValue())));
    }

    public Map<String, String> getValues(String metadataType) {
        return valuesByType.getOrDefault(metadataType, Map.of());
    }
}

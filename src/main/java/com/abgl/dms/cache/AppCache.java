package com.abgl.dms.cache;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class AppCache {

    private final AtomicReference<Map<String, Object>> objects = new AtomicReference<>(Map.of());

    public void put(String key, Object value) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(value, "Cache value must not be null");
        objects.updateAndGet(current -> {
            Map<String, Object> updated = new HashMap<>(current);
            updated.put(key, value);
            return Map.copyOf(updated);
        });
    }

    public <T> T get(String key, Class<T> objectType) {
        Objects.requireNonNull(key, "Cache key must not be null");
        Objects.requireNonNull(objectType, "Object type must not be null");
        Object value = objects.get().get(key);
        if (value == null) {
            throw new IllegalStateException("No application cache object registered for key: " + key);
        }
        return objectType.cast(value);
    }
}

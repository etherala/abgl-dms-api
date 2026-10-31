package com.abgl.dms.cache;

import com.abgl.dms.repository.AppMetadataRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class AppCacheInitializer implements ApplicationRunner {

    public static final String METADATA_DROPLIST = "metadataDropList";

    private static final Logger log = LoggerFactory.getLogger(AppCacheInitializer.class);

    private final AppCache appCache;
    private final AppMetadataRepository appMetadataRepository;

    public AppCacheInitializer(AppCache appCache, AppMetadataRepository appMetadataRepository) {
        this.appCache = appCache;
        this.appMetadataRepository = appMetadataRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        Map<String, Map<String, String>> valuesByType = appMetadataRepository.findDropListMetadata();
        MetadataDropList metadataDropList = new MetadataDropList(valuesByType);
        appCache.put(METADATA_DROPLIST, metadataDropList);

        int valueCount = valuesByType.values().stream().mapToInt(Map::size).sum();
        log.info("Loaded app metadata cache: types={}, values={}", valuesByType.size(), valueCount);
    }
}

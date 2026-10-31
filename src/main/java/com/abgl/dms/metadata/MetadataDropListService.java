package com.abgl.dms.metadata;

import com.abgl.dms.cache.AppCache;
import com.abgl.dms.cache.AppCacheInitializer;
import com.abgl.dms.cache.MetadataDropList;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class MetadataDropListService {

    private final AppCache appCache;

    public MetadataDropListService(AppCache appCache) {
        this.appCache = appCache;
    }

    public List<MetadataDropListItem> getValues(String metadataType) {
        MetadataDropList metadataDropList = appCache.get(
                AppCacheInitializer.METADATA_DROPLIST,
                MetadataDropList.class);

        return metadataDropList.getValues(metadataType).entrySet().stream()
                .map(entry -> new MetadataDropListItem(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparing(
                                MetadataDropListItem::description,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(MetadataDropListItem::code))
                .toList();
    }
}

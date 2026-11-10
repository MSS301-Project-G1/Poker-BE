package com.msspoker.shopservice.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.msspoker.shopservice.config.SharedAssetProperties;
import com.msspoker.shopservice.util.AssetUrlSigner;
import org.mapstruct.factory.Mappers;
import org.springframework.test.util.ReflectionTestUtils;

/** Mappers outside Spring lack their injected AssetUrlSigner; these get one that leaves links unchanged. */
public final class TestMappers {

    private TestMappers() {
    }

    public static <T> T withUnsignedLinks(Class<T> type) {
        T mapper = Mappers.getMapper(type);
        AssetUrlSigner unsigned = new AssetUrlSigner(
                new SharedAssetProperties(null, null, null, null, null, null, false), new ObjectMapper());
        ReflectionTestUtils.setField(mapper, "assetUrlSigner", unsigned);
        return mapper;
    }
}

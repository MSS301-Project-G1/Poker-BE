package com.msspoker.shopservice.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;

/**
 * Utility helper for safely merging and updating Product metadata JSON without losing existing fields (e.g. soundUrl).
 */
@Component
@Slf4j
public class MetadataJsonMerger {

    private final ObjectMapper objectMapper;

    public MetadataJsonMerger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Merge fields from incomingJson into existingJson.
     * Any existing keys not present in incomingJson are preserved.
     */
    public String mergeManifestWithExistingMetadata(String existingJson, String incomingJson) {
        return merge(existingJson, incomingJson);
    }

    public String merge(String existingJson, String incomingJson) {
        if (existingJson == null || existingJson.isBlank()) {
            return incomingJson;
        }
        if (incomingJson == null || incomingJson.isBlank()) {
            return existingJson;
        }

        try {
            JsonNode existingNode = objectMapper.readTree(existingJson);
            JsonNode incomingNode = objectMapper.readTree(incomingJson);

            if (existingNode.isObject() && incomingNode.isObject()) {
                ObjectNode merged = ((ObjectNode) existingNode).deepCopy();
                Iterator<Map.Entry<String, JsonNode>> fields = incomingNode.fields();
                while (fields.hasNext()) {
                    Map.Entry<String, JsonNode> field = fields.next();
                    merged.set(field.getKey(), field.getValue());
                }
                return objectMapper.writeValueAsString(merged);
            }

            return incomingJson;
        } catch (JsonProcessingException e) {
            log.warn("Failed to merge metadata JSON, falling back to incomingJson: {}", e.getMessage());
            return incomingJson;
        }
    }

    /**
     * Set or replace a single String field (e.g. soundUrl).
     */
    public String setStringField(String existingJson, String fieldName, String value) {
        try {
            ObjectNode root = resolveObjectNode(existingJson);
            if (value != null && !value.isBlank()) {
                root.put(fieldName, value);
            } else {
                root.remove(fieldName);
            }
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException e) {
            log.error("Failed to set string field {} in metadata JSON: {}", fieldName, e.getMessage(), e);
            return existingJson;
        }
    }

    /**
     * Remove a single field from metadata JSON.
     */
    public String removeField(String existingJson, String fieldName) {
        if (existingJson == null || existingJson.isBlank()) {
            return existingJson;
        }
        try {
            JsonNode node = objectMapper.readTree(existingJson);
            if (node.isObject() && node.has(fieldName)) {
                ObjectNode objectNode = ((ObjectNode) node).deepCopy();
                objectNode.remove(fieldName);
                return objectMapper.writeValueAsString(objectNode);
            }
            return existingJson;
        } catch (JsonProcessingException e) {
            log.warn("Failed to remove field {} from metadata JSON: {}", fieldName, e.getMessage());
            return existingJson;
        }
    }

    /**
     * Get a string field from metadata JSON, or null if missing.
     */
    public String getStringField(String metadataJson, String fieldName) {
        if (metadataJson == null || metadataJson.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(metadataJson);
            if (node.hasNonNull(fieldName)) {
                return node.get(fieldName).asText();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private ObjectNode resolveObjectNode(String json) {
        if (json != null && !json.isBlank()) {
            try {
                JsonNode node = objectMapper.readTree(json);
                if (node.isObject()) {
                    return ((ObjectNode) node).deepCopy();
                }
            } catch (Exception ignored) {
            }
        }
        return objectMapper.createObjectNode();
    }
}

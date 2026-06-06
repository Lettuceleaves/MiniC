package minic.uiapi.web;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * JSON codec for UIAPI DTO transport.
 */
public final class MiniCUiApiJson {
    private final ObjectMapper mapper;

    public MiniCUiApiJson() {
        mapper = JsonMapper.builder()
                .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY)
                .build();
    }

    public String write(Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("failed to serialize UIAPI JSON", exception);
        }
    }

    byte[] writeBytes(Object value) throws JsonProcessingException {
        return mapper.writeValueAsBytes(value);
    }

    public <T> T read(String json, Class<T> type) throws JsonProcessingException {
        return mapper.readValue(json, type);
    }

    <T> T read(InputStream input, Class<T> type) throws IOException {
        return mapper.readValue(input, type);
    }

    public <T> List<T> readList(String json, Class<T> elementType) throws JsonProcessingException {
        return mapper.readValue(
                json,
                mapper.getTypeFactory().constructCollectionType(List.class, elementType)
        );
    }
}

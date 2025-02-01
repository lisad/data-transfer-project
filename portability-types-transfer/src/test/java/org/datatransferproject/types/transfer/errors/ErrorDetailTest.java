package org.datatransferproject.types.transfer.errors;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ErrorDetailTest {

    private ObjectMapper objectMapper;
    private static final String ERROR_DETAIL_JSON =
        "{\"id\":\"id\",\"title\":\"exception\",\"exception\":\"exception message\"}";
    private final ErrorDetail errorDetail = ErrorDetail.builder()
        .setException("exception message")
        .setId("id")
        .setTitle("exception")
        .build();

    @Before
    public void setUp() {
        objectMapper = new ObjectMapper();
    }

    @Test
    public void shouldSerializeErrorDetail() throws JsonProcessingException {

        final String result = objectMapper.writeValueAsString(errorDetail);

        assertEquals(ERROR_DETAIL_JSON, result);
    }

    @Test
    public void shouldDeserializeErrorDetail() throws JsonProcessingException {

        ErrorDetail result = objectMapper.readValue(ERROR_DETAIL_JSON, ErrorDetail.class);

        assertEquals(errorDetail, result);
    }
}

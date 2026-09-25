package com.sportsbooking.admin.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.biz.exception.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponseTest {

    @Test
    void buildsSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("data");

        assertThat(response.getData()).isEqualTo("data");
        assertThat(response.getCode()).isZero();
        assertThat(response.getMsg()).isEqualTo("成功");
    }

    @Test
    void buildsFailedResponse() {
        ApiResponse<Void> response = ApiResponse.failed("参数不正确");

        assertThat(response.getData()).isNull();
        assertThat(response.getCode()).isEqualTo(1);
        assertThat(response.getMsg()).isEqualTo("参数不正确");
    }

    @Test
    void serializesOnlyGlobalResponseFields() {
        JsonNode json = new ObjectMapper().valueToTree(ApiResponse.success("data"));

        assertThat(json.size()).isEqualTo(3);
        assertThat(json.get("data").asText()).isEqualTo("data");
        assertThat(json.get("code").asInt()).isZero();
        assertThat(json.get("msg").asText()).isEqualTo("成功");
    }

    @Test
    void keepsHttpStatusForFailedResponse() {
        ApiException exception = new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);

        ResponseEntity<ApiResponse<Object>> response = new ApiExceptionHandler().handleApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(1);
        assertThat(response.getBody().getMsg()).isEqualTo("参数不正确");
        assertThat(response.getBody().getData()).isNull();
    }

    @Test
    void keepsStructuredBusinessErrorData() {
        List<String> conflictDates = List.of("2026-09-16", "2026-09-18");
        ApiException exception = new ApiException(HttpStatus.CONFLICT, ErrorCode.BATCH_SLOT_CONFLICT, conflictDates);

        ResponseEntity<ApiResponse<Object>> response = new ApiExceptionHandler().handleApiException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isEqualTo(conflictDates);
    }
}

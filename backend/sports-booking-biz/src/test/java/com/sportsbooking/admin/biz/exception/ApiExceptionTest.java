package com.sportsbooking.admin.biz.exception;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.base.enums.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ApiExceptionTest {

    @Test
    void usesErrorCodeAndDefaultMessageFromEnum() {
        ApiException exception = new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);

        assertThat(ErrorCode.USER_DISABLED.getKey()).isEqualTo("1001");
        assertThat(ErrorCode.USER_DISABLED.getDescription()).isEqualTo("用户不可用");
        assertThat(exception.getMessage()).isEqualTo("用户不可用");
        assertThat(ErrorCode.NOT_FOUND.getKey()).isEqualTo("NOT_FOUND");
    }
}

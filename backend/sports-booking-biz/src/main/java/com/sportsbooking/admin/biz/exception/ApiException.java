package com.sportsbooking.admin.biz.exception;

import org.springframework.http.HttpStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;

/**
 * 功能描述: 携带 HTTP 状态、业务错误码和用户可读消息的业务异常。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final ErrorCode code;

    /**
     * 功能描述: 使用错误码中配置的默认提示语创建业务异常。
     *
     * @param status HTTP 状态
     * @param code 业务错误码
     * @author gjm
    */
    public ApiException(HttpStatus status, ErrorCode code) {
        super(code.getDescription());
        this.status = status;
        this.code = code;
    }

    /**
     * 功能描述: 获取业务异常对应的 HTTP 状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @return HTTP 状态
     * @author gjm
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * 功能描述: 获取稳定业务错误码。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 业务错误码
     * @author gjm
     */
    public ErrorCode getCode() {
        return code;
    }
}

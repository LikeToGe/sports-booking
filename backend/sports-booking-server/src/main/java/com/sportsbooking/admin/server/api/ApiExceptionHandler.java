package com.sportsbooking.admin.server.api;

import com.sportsbooking.admin.biz.exception.ApiException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.sportsbooking.admin.base.enums.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 功能描述: 将服务端异常转换为统一错误响应，并保留未知异常诊断信息。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    /**
     * 功能描述: 将已知业务异常转换为原始 HTTP 状态和统一失败响应。
     * 创建时间: 2026/08/28 16:29
     *
     * @param exception 业务异常
     * @return 统一错误响应
     * @author gjm
     */
    @ExceptionHandler(ApiException.class)
    ResponseEntity<ApiResponse<Object>> handleApiException(ApiException exception) {
        return ResponseEntity.status(exception.getStatus()).body(error(exception.getCode(), exception.getData()));
    }

    /** 请求值解析失败属于客户端参数错误，不记录可能含身份或联系资料的原始值。 */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> handleInvalidRequest() {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error(ErrorCode.INVALID_ARGUMENT));
    }

    /**
     * 功能描述: 将数据库唯一键冲突转换为重复提交错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @param exception 唯一键冲突异常
     * @return 统一错误响应
     * @author gjm
     */
    @ExceptionHandler(DuplicateKeyException.class)
    ResponseEntity<ApiResponse<Void>> handleDuplicateKey(DuplicateKeyException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error(ErrorCode.DUPLICATE));
    }

    /**
     * 功能描述: 记录未知异常并向客户端返回不含敏感信息的服务错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @param exception 未知异常
     * @return 统一错误响应
     * @author gjm
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception) {
        // 客户端不返回堆栈和敏感数据，完整异常只写入服务端日志供故障定位。
        LOGGER.error("未处理的服务端异常", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error(ErrorCode.INTERNAL_ERROR));
    }

    /**
     * 功能描述: 构造统一错误响应结构。
     * 创建时间: 2026/08/28 16:29
     *
     * @param code 业务错误码
     * @return 统一错误响应体
     * @author gjm
     */
    private ApiResponse<Void> error(ErrorCode code) {
        return ApiResponse.failed(code.getDescription());
    }

    /**
     * 功能描述: 构造带结构化详情的失败响应。
     *
     * @param code 业务错误码
     * @param data 错误详情
     * @return 统一错误响应体
     * @author gjm
     */
    private ApiResponse<Object> error(ErrorCode code, Object data) {
        return ApiResponse.failed(code.getDescription(), data);
    }
}

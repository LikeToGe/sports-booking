package com.sportsbooking.admin.server.api;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
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
        return ResponseEntity.status(exception.getStatus()).body(ApiResponse.failed(exception.getMessage(), exception.getData()));
    }

    /**
     * 指出缺失或格式错误的字段，不返回原始值和解析器异常文本。
     *
     * @param exception 请求参数解析异常
     * @return 包含字段说明的参数错误
     * @author gjm
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class,
            MissingServletRequestParameterException.class, MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiResponse<Void>> handleInvalidRequest(Exception exception) {
        String message = "提交内容格式不正确，请检查后重试";
        if (exception instanceof MissingServletRequestParameterException missing) {
            message = "请填写" + parameterLabel(missing.getParameterName());
        } else if (exception instanceof MissingServletRequestPartException missing) {
            message = "请选择" + parameterLabel(missing.getRequestPartName());
        } else if (exception instanceof MethodArgumentTypeMismatchException mismatch) {
            message = parameterLabel(mismatch.getName()) + formatHint(mismatch.getRequiredType());
        } else if (exception.getCause() instanceof JsonMappingException mapping && !mapping.getPath().isEmpty()) {
            JsonMappingException.Reference field = mapping.getPath().stream()
                    .filter(reference -> reference.getFieldName() != null).reduce((first, last) -> last)
                    .orElse(mapping.getPath().get(0));
            String label = parameterLabel(field.getFieldName());
            try {
                if (field.getFieldName() == null || field.getFrom() == null) throw new NoSuchFieldException();
                Class<?> owner = field.getFrom() instanceof Class<?> type ? type : field.getFrom().getClass();
                Schema schema = owner.getDeclaredField(field.getFieldName()).getAnnotation(Schema.class);
                if (schema != null && !schema.description().isBlank()) label = schema.description();
            } catch (ReflectiveOperationException ignored) {
                // 未标注的嵌套字段仍使用参数名称，避免把解析器中的敏感值返回给前端。
            }
            message = label + formatHint(mapping instanceof InvalidFormatException invalid ? invalid.getTargetType() : null);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ApiResponse.failed(message));
    }

    /**
     * 将上传大小限制转为可直接展示的操作提示。
     *
     * @return 文件过大的错误响应
     * @author gjm
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiResponse<Void>> handleUploadTooLarge() {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.failed("上传文件过大，请选择 30 MB 以内的文件"));
    }

    /**
     * 将常用查询参数转为页面使用的字段名称。
     *
     * @param name 参数名称
     * @return 字段说明
     * @author gjm
     */
    private String parameterLabel(String name) {
        if (name == null) return "请求参数";
        return switch (name) {
            case "date", "targetDate" -> "日期";
            case "startDate" -> "开始日期";
            case "endDate" -> "结束日期";
            case "startTime", "slotStart" -> "开始时间";
            case "endTime", "slotEnd" -> "结束时间";
            case "courtId" -> "场地";
            case "sportTypeId" -> "运动类型";
            case "venueId" -> "场馆";
            case "file" -> "上传文件";
            case "billingMonth" -> "结算月份";
            case "minPriceFen" -> "最低价格（分）";
            case "maxPriceFen" -> "最高价格（分）";
            case "page" -> "页码";
            case "size" -> "每页条数";
            case "reason", "resolution" -> "处理原因";
            case "days" -> "天数";
            case "userId" -> "用户 ID";
            default -> "参数「" + name + "」";
        };
    }

    /**
     * 按目标类型给出填写格式，不回显提交值。
     *
     * @param type 参数目标类型
     * @return 格式要求
     * @author gjm
     */
    private String formatHint(Class<?> type) {
        if (type == java.time.LocalDate.class) return "格式应为 yyyy-MM-dd";
        if (type == java.time.LocalTime.class) return "格式应为 HH:mm";
        if (type == Integer.class || type == Long.class || type == int.class || type == long.class) return "必须填写整数";
        return "格式不正确，请检查后重试";
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


}

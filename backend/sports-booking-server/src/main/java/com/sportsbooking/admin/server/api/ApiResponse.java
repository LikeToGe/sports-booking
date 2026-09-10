package com.sportsbooking.admin.server.api;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 功能描述: HTTP 接口统一响应。
 * 创建时间: 2026/08/28 16:29
 *
 * @param <T> 业务数据类型
 * @author gjm
 */
@Data
@Accessors(chain = true)
public class ApiResponse<T> {
    private static final int SUCCESS_CODE = 0;
    private static final int FAILED_CODE = 1;

    /**
     * 业务数据
     */
    private T data;

    /**
     * 调用结果编码，0 表示成功，1 表示失败
     */
    private Integer code;

    /**
     * 调用结果提示
     */
    private String msg;

    private ApiResponse() {
    }

    /**
     * 功能描述: 包装业务数据为统一成功响应。
     * 创建时间: 2026/08/28 16:29
     *
     * @param data 业务数据
     * @param <T> 业务数据类型
     * @return 统一成功响应
     * @author gjm
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>()
                .setData(data)
                .setCode(SUCCESS_CODE)
                .setMsg("成功");
    }

    /**
     * 功能描述: 包装错误提示为统一失败响应。
     * 创建时间: 2026/09/03 11:50
     *
     * @param msg 错误提示
     * @param <T> 业务数据类型
     * @return 统一失败响应
     * @author gjm
     */
    public static <T> ApiResponse<T> failed(String msg) {
        return new ApiResponse<T>()
                .setCode(FAILED_CODE)
                .setMsg(msg);
    }
}

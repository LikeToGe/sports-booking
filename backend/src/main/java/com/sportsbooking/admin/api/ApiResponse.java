package com.sportsbooking.admin.api;

public class ApiResponse<T> {
    private final T data;
    private final int code;
    private final String serviceCode;
    private final String msg;

    private ApiResponse(T data, int code, String msg) {
        this.data = data;
        this.code = code;
        this.serviceCode = "10000";
        this.msg = msg;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<T>(data, 1, "业务处理成功");
    }

    public static <T> ApiResponse<T> failure(T data, int code, String msg) {
        return new ApiResponse<T>(data, code, msg);
    }

    public T getData() { return data; }
    public int getCode() { return code; }
    public String getServiceCode() { return serviceCode; }
    public String getMsg() { return msg; }
}

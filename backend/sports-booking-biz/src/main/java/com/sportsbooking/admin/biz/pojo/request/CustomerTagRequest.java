package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 创建场馆内顾客标签请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerTagRequest {
    /** 标签名称，最多 40 个字符。 */
    @Schema(description = "标签名称，最多 40 个字符")
    @ApiModelProperty(value = "标签名称，最多 40 个字符")
    private String name;
}

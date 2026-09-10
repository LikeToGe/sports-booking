package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;

/**
 * 功能描述: 小程序员工身份绑定请求。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Schema(description = "小程序员工身份绑定请求")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeBindRequest {

    /**
     * 一次性员工绑定码
     */
    @Schema(description = "一次性员工绑定码")
    @ApiModelProperty(value = "一次性员工绑定码")
    private String bindingCode;
}

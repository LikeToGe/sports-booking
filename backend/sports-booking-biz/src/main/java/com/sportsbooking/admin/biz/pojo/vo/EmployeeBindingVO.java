package com.sportsbooking.admin.biz.pojo.vo;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 一次性员工绑定码。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeBindingVO {

    /**
     * 员工 ID
     */
    @Schema(description = "员工 ID")
    @ApiModelProperty(value = "员工 ID")
    private Long employeeId;

    /**
     * 一次性员工绑定码
     */
    @Schema(description = "一次性员工绑定码")
    @ApiModelProperty(value = "一次性员工绑定码")
    private String bindingCode;

    /**
     * 过期时间
     */
    @Schema(description = "过期时间")
    @ApiModelProperty(value = "过期时间")
    private LocalDateTime expiresAt;
}

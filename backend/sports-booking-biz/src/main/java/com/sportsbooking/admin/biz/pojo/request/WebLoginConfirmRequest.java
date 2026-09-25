package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 员工确认 Web 登录场馆请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WebLoginConfirmRequest {
    /** 员工本次用于登录 Web 的场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private long venueId;
}

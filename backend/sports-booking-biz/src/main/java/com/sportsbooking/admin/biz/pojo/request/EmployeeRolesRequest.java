package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 员工角色整体替换请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeRolesRequest {
    /** 要整体替换为的场馆角色 ID。 */
    @Schema(description = "角色 ID 列表")
    @ApiModelProperty(value = "角色 ID 列表")
    private List<Long> roleIds;
}

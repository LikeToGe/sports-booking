package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.po.Employee;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 员工接口返回模型，不包含绑定码摘要等认证数据。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 场馆 ID
     */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;

    /**
     * 用户 ID
     */
    @Schema(description = "用户 ID")
    @ApiModelProperty(value = "用户 ID")
    private Long userId;

    /**
     * 名称
     */
    @Schema(description = "名称")
    @ApiModelProperty(value = "名称")
    private String name;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private EnableStatus status;

    /**
     * 功能描述: 将员工实体转换为不含认证字段的接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param employee 员工持久化实体
     * @return 员工接口模型
     * @author gjm
     */
    public static EmployeeVO from(Employee employee) {
        return new EmployeeVO(employee.id, employee.venueId, employee.userId, employee.name, employee.status);
    }
}

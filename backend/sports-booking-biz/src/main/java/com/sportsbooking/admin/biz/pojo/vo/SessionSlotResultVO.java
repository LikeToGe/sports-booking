package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.SessionSlotStatus;
import com.sportsbooking.admin.base.po.SessionSlot;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 新建具体场次接口返回模型。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SessionSlotResultVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 场地 ID
     */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private Long courtId;

    /**
     * 运动类型 ID
     */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;

    /**
     * 场次日期
     */
    @Schema(description = "场次日期")
    @ApiModelProperty(value = "场次日期")
    private LocalDate slotDate;

    /**
     * 开始时间
     */
    @Schema(description = "开始时间")
    @ApiModelProperty(value = "开始时间")
    private Instant startAt;

    /**
     * 结束时间
     */
    @Schema(description = "结束时间")
    @ApiModelProperty(value = "结束时间")
    private Instant endAt;

    /**
     * 价格，单位为分
     */
    @Schema(description = "价格，单位为分")
    @ApiModelProperty(value = "价格，单位为分")
    private Integer priceFen;

    /**
     * 状态
     */
    @Schema(description = "状态")
    @ApiModelProperty(value = "状态")
    private SessionSlotStatus status;

    /**
     * 功能描述: 将具体场次实体转换为接口模型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param sessionSlot 具体场次实体
     * @return 具体场次接口模型
     * @author gjm
     */
    public static SessionSlotResultVO from(SessionSlot sessionSlot) {
        return new SessionSlotResultVO(sessionSlot.id, sessionSlot.courtId, sessionSlot.sportTypeId,
                sessionSlot.slotDate, sessionSlot.startAt, sessionSlot.endAt,
                sessionSlot.priceFen, sessionSlot.status);
    }
}

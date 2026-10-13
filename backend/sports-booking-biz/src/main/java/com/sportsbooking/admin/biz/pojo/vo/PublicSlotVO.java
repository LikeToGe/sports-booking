package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PublicSlotBO;
import lombok.Data;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/** 仅公开完整具体场次与可订状态，不暴露占用者信息。 */
@Data
public class PublicSlotVO {
    /** 具体场次 ID。 */
    @Schema(description = "具体场次 ID")
    @ApiModelProperty(value = "具体场次 ID")
    private Long id;
    /** 场地 ID。 */
    @Schema(description = "场地 ID")
    @ApiModelProperty(value = "场地 ID")
    private Long courtId;
    /** 场地名称。 */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;
    /** 运动类型 ID。 */
    @Schema(description = "运动类型 ID")
    @ApiModelProperty(value = "运动类型 ID")
    private Long sportTypeId;
    /** 运动名称。 */
    @Schema(description = "运动名称")
    @ApiModelProperty(value = "运动名称")
    private String sportName;
    /** 真实开始时间。 */
    @Schema(description = "真实开始时间")
    @ApiModelProperty(value = "真实开始时间")
    private String startAt;
    /** 真实结束时间。 */
    @Schema(description = "真实结束时间")
    @ApiModelProperty(value = "真实结束时间")
    private String endAt;
    /** 当前单场价格，单位为分。 */
    @Schema(description = "当前单场价格，单位为分")
    @ApiModelProperty(value = "当前单场价格，单位为分")
    private Integer priceFen;
    /** 公开状态：OPEN、HELD、BOOKED、CLOSED 或 UNAVAILABLE。 */
    @Schema(description = "公开状态：OPEN、HELD、BOOKED、CLOSED 或 UNAVAILABLE")
    @ApiModelProperty(value = "公开状态：OPEN、HELD、BOOKED、CLOSED 或 UNAVAILABLE")
    private String status;
    /** 按当前规则计算的取消截止时间。 */
    @Schema(description = "按当前规则计算的取消截止时间")
    @ApiModelProperty(value = "按当前规则计算的取消截止时间")
    private String cancelDeadline;

    public static PublicSlotVO from(PublicSlotBO source) {
        PublicSlotVO result = new PublicSlotVO();
        result.setId(source.getId());
        result.setCourtId(source.getCourtId());
        result.setCourtName(source.getCourtName());
        result.setSportTypeId(source.getSportTypeId());
        result.setSportName(source.getSportName());
        result.setStartAt(source.getStartAt());
        result.setEndAt(source.getEndAt());
        result.setPriceFen(source.getPriceFen());
        result.setStatus(source.getStatus());
        result.setCancelDeadline(source.getCancelDeadline());
        return result;
    }
}

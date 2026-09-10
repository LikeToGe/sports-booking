package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.StaffVerificationRecordBO;
import com.sportsbooking.admin.base.enums.VerificationMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 员工本人核销记录。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffVerificationRecordVO {

    /**
     * 主键 ID
     */
    @Schema(description = "主键 ID")
    @ApiModelProperty(value = "主键 ID")
    private Long id;

    /**
     * 预约 ID
     */
    @Schema(description = "预约 ID")
    @ApiModelProperty(value = "预约 ID")
    private Long bookingId;

    /**
     * 预约编号
     */
    @Schema(description = "预约编号")
    @ApiModelProperty(value = "预约编号")
    private String bookingNo;

    /**
     * 场地名称
     */
    @Schema(description = "场地名称")
    @ApiModelProperty(value = "场地名称")
    private String courtName;

    /**
     * 运动名称
     */
    @Schema(description = "运动名称")
    @ApiModelProperty(value = "运动名称")
    private String sportName;

    /**
     * 联系人姓名
     */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /**
     * 核销方式
     */
    @Schema(description = "核销方式")
    @ApiModelProperty(value = "核销方式")
    private VerificationMethod method;

    /**
     * 核销时间
     */
    @Schema(description = "核销时间")
    @ApiModelProperty(value = "核销时间")
    private String verifiedAt;

    public static StaffVerificationRecordVO from(StaffVerificationRecordBO source) {
        return new StaffVerificationRecordVO(source.getId(), source.getBookingId(), source.getBookingNo(),
                source.getCourtName(), source.getSportName(), source.getContactName(), source.getMethod(),
                source.getVerifiedAt());
    }
}

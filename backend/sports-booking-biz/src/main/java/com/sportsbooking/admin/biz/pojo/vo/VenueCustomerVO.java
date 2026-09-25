package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueCustomerBO;
import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 场馆后台顾客列表与详情模型。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueCustomerVO {
    /** 顾客档案 ID。 */
    @Schema(description = "顾客档案 ID")
    @ApiModelProperty(value = "顾客档案 ID")
    private Long customerId;

    /** 联系人姓名。 */
    @Schema(description = "联系人姓名")
    @ApiModelProperty(value = "联系人姓名")
    private String contactName;

    /** 完整手机号，仅返回给所属场馆管理员。 */
    @Schema(description = "完整手机号")
    @ApiModelProperty(value = "完整手机号")
    private String phone;

    /** 顾客或会员状态。 */
    @Schema(description = "顾客或会员状态")
    @ApiModelProperty(value = "顾客或会员状态")
    private VenueCustomerStatus status;

    /** 场馆内会员码。 */
    @Schema(description = "场馆内会员码")
    @ApiModelProperty(value = "场馆内会员码")
    private String memberCode;

    /** 首次充值激活会员的时间。 */
    @Schema(description = "首次充值激活会员的时间")
    @ApiModelProperty(value = "首次充值激活会员的时间")
    private Instant activatedAt;

    /** 顾客建档时间。 */
    @Schema(description = "顾客建档时间")
    @ApiModelProperty(value = "顾客建档时间")
    private LocalDateTime createdAt;

    /**
     * 功能描述: 将内部查询投影转换为场馆后台接口模型。
     *
     * @param customer 顾客查询投影
     * @return 场馆后台顾客模型
     * @author gjm
     */
    public static VenueCustomerVO from(VenueCustomerBO customer) {
        return new VenueCustomerVO(customer.getCustomerId(), customer.getContactName(), customer.getPhone(),
                customer.getStatus(), customer.getMemberCode(), customer.getActivatedAt(), customer.getCreatedAt());
    }
}

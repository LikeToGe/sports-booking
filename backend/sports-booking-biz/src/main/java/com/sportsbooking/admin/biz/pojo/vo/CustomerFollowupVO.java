package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.CustomerFollowupBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆顾客回访待办。备注只返回给当前场馆授权人员。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerFollowupVO {
    /** 待办 ID。 */
    @Schema(description = "待办 ID")
    @ApiModelProperty(value = "待办 ID")
    private Long id;
    /** 顾客档案 ID。 */
    @Schema(description = "顾客档案 ID")
    @ApiModelProperty(value = "顾客档案 ID")
    private Long customerId;
    /** 顾客姓名。 */
    @Schema(description = "顾客姓名")
    @ApiModelProperty(value = "顾客姓名")
    private String contactName;
    /** 待办目的。 */
    @Schema(description = "待办目的")
    @ApiModelProperty(value = "待办目的")
    private String purpose;
    /** 来源分组。 */
    @Schema(description = "来源分组")
    @ApiModelProperty(value = "来源分组")
    private String sourceGroup;
    /** 状态：PENDING、COMPLETED、NOT_NEEDED。 */
    @Schema(description = "状态：PENDING、COMPLETED、NOT_NEEDED")
    @ApiModelProperty(value = "状态：PENDING、COMPLETED、NOT_NEEDED")
    private String status;
    /** 仅当前场馆授权人员可见的内部备注。 */
    @Schema(description = "仅当前场馆授权人员可见的内部备注")
    @ApiModelProperty(value = "仅当前场馆授权人员可见的内部备注")
    private String note;
    /** 创建人姓名快照。 */
    @Schema(description = "创建人姓名快照")
    @ApiModelProperty(value = "创建人姓名快照")
    private String createdByName;
    /** 创建时间，UTC Instant。 */
    @Schema(description = "创建时间，UTC Instant")
    @ApiModelProperty(value = "创建时间，UTC Instant")
    private Instant createdAt;
    /** 处理人姓名快照。 */
    @Schema(description = "处理人姓名快照")
    @ApiModelProperty(value = "处理人姓名快照")
    private String handledByName;
    /** 处理时间，UTC Instant。 */
    @Schema(description = "处理时间，UTC Instant")
    @ApiModelProperty(value = "处理时间，UTC Instant")
    private Instant handledAt;
    /** 是否存在场次开始时间晚于处理时间的有效预约；不表示预约创建于处理后或由回访导致。 */
    @Schema(description = "是否存在处理后日期的有效预约场次，按场次开始时间判断；不表示预约创建于处理后或由回访导致")
    @ApiModelProperty(value = "是否存在处理后日期的有效预约场次，按场次开始时间判断；不表示预约创建于处理后或由回访导致")
    private Boolean rebookedAfterHandled;
    /** 场次开始时间晚于处理时间的首笔有效预约场次开始时间，UTC Instant。 */
    @Schema(description = "首个处理后日期的有效预约场次开始时间，按场次时间判断，UTC Instant")
    @ApiModelProperty(value = "首个处理后日期的有效预约场次开始时间，按场次时间判断，UTC Instant")
    private Instant rebookedAfterHandledAt;
    /** 处理后日期首个有效预约场次来源：ONLINE 或 OFFLINE。 */
    @Schema(description = "处理后日期首个有效预约场次来源：ONLINE 或 OFFLINE")
    @ApiModelProperty(value = "处理后日期首个有效预约场次来源：ONLINE 或 OFFLINE")
    private String rebookedAfterHandledSource;

    /** 转换场馆内回访投影，不公开平台个人资料。 */
    public static CustomerFollowupVO from(CustomerFollowupBO source) {
        return new CustomerFollowupVO(source.getId(), source.getCustomerId(), source.getContactName(),
                source.getPurpose(), source.getSourceGroup(), source.getStatus(), source.getNote(),
                source.getCreatedByName(), utc(source.getCreatedAt()), source.getHandledByName(), utc(source.getHandledAt()),
                source.getRebookedAfterHandled() == null ? null : source.getRebookedAfterHandled() == 1,
                utc(source.getRebookedAfterHandledAt()), source.getRebookedAfterHandledSource());
    }

    private static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

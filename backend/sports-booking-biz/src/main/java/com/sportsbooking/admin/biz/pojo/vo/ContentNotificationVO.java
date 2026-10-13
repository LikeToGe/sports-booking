package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.Notification;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户消息，仅返回当前用户需要的展示和导航事实。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class ContentNotificationVO {
    /** 消息 ID。 */
    @Schema(description = "消息 ID") @ApiModelProperty("消息 ID") private Long id;
    /** 标题。 */
    @Schema(description = "标题") @ApiModelProperty("标题") private String title;
    /** 正文。 */
    @Schema(description = "正文") @ApiModelProperty("正文") private String content;
    /** 关联业务类型。 */
    @Schema(description = "业务类型") @ApiModelProperty("业务类型") private String businessType;
    /** 关联业务 ID。 */
    @Schema(description = "业务 ID") @ApiModelProperty("业务 ID") private Long businessId;
    /** 提醒登记 ID。 */
    @Schema(description = "提醒登记 ID") @ApiModelProperty("提醒登记 ID") private Long subscriptionId;
    /** 阅读时间。 */
    @Schema(description = "阅读时间") @ApiModelProperty("阅读时间") private Instant readAt;
    /** 创建时间。 */
    @Schema(description = "创建时间") @ApiModelProperty("创建时间") private Instant createdAt;
    /** 消息类型。 */
    @Schema(description = "消息类型") @ApiModelProperty("消息类型") private String type;
    /** Mock 投递状态。 */
    @Schema(description = "投递状态") @ApiModelProperty("投递状态") private String status;

    public static ContentNotificationVO from(Notification row) {
        return new ContentNotificationVO(row.id, row.title, row.content, row.businessType,
                row.businessId, row.subscriptionId,
                row.readAt == null ? null : row.readAt.toInstant(ZoneOffset.UTC),
                row.createdAt == null ? null : row.createdAt.toInstant(ZoneOffset.UTC),
                row.type == null ? null : row.type.getKey(),
                row.status == null ? null : row.status.getKey());
    }
}

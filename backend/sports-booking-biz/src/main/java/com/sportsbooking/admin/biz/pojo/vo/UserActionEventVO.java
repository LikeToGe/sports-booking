package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.UserActionEventBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 原始动作事件，未推导转化率。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class UserActionEventVO {
    /** 事件 ID。 */
    @Schema(description = "事件 ID") @ApiModelProperty("事件 ID") private Long id;
    /** 用户 ID。 */
    @Schema(description = "用户 ID") @ApiModelProperty("用户 ID") private Long userId;
    /** 事件类型。 */
    @Schema(description = "事件类型") @ApiModelProperty("事件类型") private String eventType;
    /** 目标类型。 */
    @Schema(description = "目标类型") @ApiModelProperty("目标类型") private String targetType;
    /** 目标 ID。 */
    @Schema(description = "目标 ID") @ApiModelProperty("目标 ID") private Long targetId;
    /** 发生时间。 */
    @Schema(description = "发生时间") @ApiModelProperty("发生时间") private LocalDateTime occurredAt;
    public static UserActionEventVO from(UserActionEventBO row) {
        return new UserActionEventVO(row.id,row.userId,row.eventType,row.targetType,row.targetId,row.occurredAt);
    }
}

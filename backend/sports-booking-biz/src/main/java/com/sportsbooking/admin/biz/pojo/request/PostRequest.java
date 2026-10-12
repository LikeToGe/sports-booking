package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/** 用户纯文字动态发布或修改请求。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    @Schema(description = "动态正文，最多 2000 字") @ApiModelProperty("动态正文，最多 2000 字")
    private String body;
    @Schema(description = "平台运动类型 ID") @ApiModelProperty("平台运动类型 ID")
    private Long sportTypeId;
    @Schema(description = "可选关联场馆 ID") @ApiModelProperty("可选关联场馆 ID")
    private Long venueId;
    @Schema(description = "可选关联本人预约 ID") @ApiModelProperty("可选关联本人预约 ID")
    private Long bookingId;
    /** null 沿用旧版本，空集合移除媒体。 */
    @Schema(description = "本次新上传的媒体 ID；修改时不传则沿用旧版本")
    @ApiModelProperty("本次新上传的媒体 ID") private List<Long> uploadIds;
}

package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.UgcMediaBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 内容版本媒体，不暴露对象 key。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UgcMediaVO {
    /** 媒体 ID。 */
    @Schema(description = "媒体 ID") @ApiModelProperty("媒体 ID") private Long id;
    /** 图片或视频。 */
    @Schema(description = "媒体类型") @ApiModelProperty("媒体类型") private String mediaType;
    /** 本地受控读取地址。 */
    @Schema(description = "媒体地址") @ApiModelProperty("媒体地址") private String url;
    /** 视频时长秒数。 */
    @Schema(description = "视频时长秒数") @ApiModelProperty("视频时长秒数") private Integer durationSeconds;
    /** 媒体审核状态。 */
    @Schema(description = "媒体审核状态") @ApiModelProperty("媒体审核状态") private String moderationStatus;

    public static UgcMediaVO post(UgcMediaBO media) {
        return new UgcMediaVO(media.id, media.mediaType, "/mini/content/media/posts/" + media.id,
                media.durationSeconds, media.moderationStatus);
    }

    public static UgcMediaVO review(UgcMediaBO media) {
        return new UgcMediaVO(media.id, media.mediaType, "/public/content/media/reviews/" + media.id,
                null, media.moderationStatus);
    }
}

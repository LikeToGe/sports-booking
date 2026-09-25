package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.enums.WebLoginTicketStatus;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Web 员工扫码登录票据。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScanTicketVO {
    /** Web 端轮询和消费使用的一次性票据。 */
    @Schema(description = "一次性票据")
    @ApiModelProperty(value = "一次性票据")
    private String ticket;
    /** 小程序扫码后解析出的原始内容。 */
    @Schema(description = "二维码内容")
    @ApiModelProperty(value = "二维码内容")
    private String qrContent;
    /** Web 可直接显示的二维码图片。 */
    @Schema(description = "二维码 PNG Data URL") @ApiModelProperty(value = "二维码 PNG Data URL")
    private String qrDataUrl;
    /** 当前票据状态。 */
    @Schema(description = "票据状态")
    @ApiModelProperty(value = "票据状态")
    private WebLoginTicketStatus status;
    /** Redis 票据预计过期时间。 */
    @Schema(description = "过期时间")
    @ApiModelProperty(value = "过期时间")
    private Instant expiresAt;
}

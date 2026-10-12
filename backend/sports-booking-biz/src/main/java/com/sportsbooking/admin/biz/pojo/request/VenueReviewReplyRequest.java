package com.sportsbooking.admin.biz.pojo.request;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆官方回复正文。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueReviewReplyRequest {
    @Schema(description = "回复正文，最多 1000 字")
    @ApiModelProperty("回复正文，最多 1000 字")
    private String body;
}

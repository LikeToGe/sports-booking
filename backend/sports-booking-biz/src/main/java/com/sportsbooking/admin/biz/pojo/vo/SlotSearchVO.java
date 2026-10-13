package com.sportsbooking.admin.biz.pojo.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import com.sportsbooking.admin.base.bo.SlotSearchBO;
import org.springframework.beans.BeanUtils;

/** 按时间和价格检索的单个可预约具体场次。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SlotSearchVO extends AvailableSlotVO {
    /** 场馆 ID。 */
    @Schema(description = "场馆 ID")
    @ApiModelProperty(value = "场馆 ID")
    private Long venueId;
    /** 场馆名称。 */
    @Schema(description = "场馆名称")
    @ApiModelProperty(value = "场馆名称")
    private String venueName;
    /** 场馆所属区县，未配置时为空。 */
    @Schema(description = "场馆所属区县，未配置时为空")
    @ApiModelProperty(value = "场馆所属区县，未配置时为空")
    private String district;

    public static SlotSearchVO from(SlotSearchBO source) {
        SlotSearchVO result = new SlotSearchVO();
        BeanUtils.copyProperties(source, result);
        return result;
    }
}

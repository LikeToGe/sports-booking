package com.sportsbooking.admin.base.bo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 按时间和价格检索的单个可预约具体场次。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class SlotSearchBO extends AvailableSlotBO {
    /** 场馆 ID。 */
    private Long venueId;
    /** 场馆名称。 */
    private String venueName;
    /** 场馆所属区县，未配置时为空。 */
    private String district;
}

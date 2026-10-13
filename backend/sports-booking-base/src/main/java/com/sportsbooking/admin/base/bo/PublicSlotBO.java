package com.sportsbooking.admin.base.bo;

import lombok.Data;

/** 不含预约与占用者信息的具体场次公开投影。 */
@Data
public class PublicSlotBO {
    /** 具体场次 ID。 */
    private Long id;
    /** 场地 ID。 */
    private Long courtId;
    /** 场地名称。 */
    private String courtName;
    /** 运动类型 ID。 */
    private Long sportTypeId;
    /** 运动名称。 */
    private String sportName;
    /** 真实开始时间。 */
    private String startAt;
    /** 真实结束时间。 */
    private String endAt;
    /** 当前单场价格，单位为分。 */
    private Integer priceFen;
    /** 公开状态：OPEN、HELD、BOOKED、CLOSED 或 UNAVAILABLE。 */
    private String status;
    /** 按当前规则计算的取消截止时间。 */
    private String cancelDeadline;
}

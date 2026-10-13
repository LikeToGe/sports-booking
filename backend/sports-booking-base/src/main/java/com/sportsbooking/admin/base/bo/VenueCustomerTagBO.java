package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 标签查询投影。 */
@Data
public class VenueCustomerTagBO {
    /** 标签 ID。 */
    private Long id;
    /** 场馆 ID。 */
    private Long venueId;
    /** 标签名称。 */
    private String name;
    /** 创建后台账号 ID。 */
    private Long createdByAdminId;
    /** 创建员工 ID。 */
    private Long createdByEmployeeId;
    /** 创建人姓名快照。 */
    private String createdByName;
    /** 创建时间。 */
    private LocalDateTime createdAt;
}

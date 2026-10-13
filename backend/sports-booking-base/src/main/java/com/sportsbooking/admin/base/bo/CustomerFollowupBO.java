package com.sportsbooking.admin.base.bo;

import java.time.LocalDateTime;
import lombok.Data;

/** 顾客回访待办查询投影。 */
@Data
public class CustomerFollowupBO {
    /** 待办 ID。 */
    private Long id;
    /** 场馆 ID。 */
    private Long venueId;
    /** 顾客档案 ID。 */
    private Long customerId;
    /** 顾客姓名。 */
    private String contactName;
    /** 待办目的。 */
    private String purpose;
    /** 来源分组。 */
    private String sourceGroup;
    /** 待办状态。 */
    private String status;
    /** 场馆内部备注。 */
    private String note;
    /** 创建后台账号 ID。 */
    private Long createdByAdminId;
    /** 创建员工 ID。 */
    private Long createdByEmployeeId;
    /** 创建人姓名快照。 */
    private String createdByName;
    /** 创建时间。 */
    private LocalDateTime createdAt;
    /** 处理后台账号 ID。 */
    private Long handledByAdminId;
    /** 处理员工 ID。 */
    private Long handledByEmployeeId;
    /** 处理人姓名快照。 */
    private String handledByName;
    /** 处理时间。 */
    private LocalDateTime handledAt;
    /** 处理时间之后是否创建有效预约，0/1；未处理时为空。 */
    private Integer rebookedAfterHandled;
    /** 处理后首笔有效预约创建时间。 */
    private LocalDateTime rebookedAfterHandledAt;
    /** 处理后首笔有效预约来源。 */
    private String rebookedAfterHandledSource;
}

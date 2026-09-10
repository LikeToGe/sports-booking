package com.sportsbooking.admin.base.bo;

import com.sportsbooking.admin.base.enums.VerificationMethod;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 功能描述: 员工本人核销记录。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StaffVerificationRecordBO {

    /**
     * 主键 ID
     */
    private Long id;

    /**
     * 预约 ID
     */
    private Long bookingId;

    /**
     * 预约编号
     */
    private String bookingNo;

    /**
     * 场地名称
     */
    private String courtName;

    /**
     * 运动名称
     */
    private String sportName;

    /**
     * 联系人姓名
     */
    private String contactName;

    /**
     * 核销方式
     */
    private VerificationMethod method;

    /**
     * 核销时间
     */
    private String verifiedAt;
}

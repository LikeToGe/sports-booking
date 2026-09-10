package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.Instant;
import com.sportsbooking.admin.base.enums.VerificationMethod;

/**
 * 功能描述: 成功核销记录持久化实体。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@TableName("s_verification")
public class Verification {
    /** 主键。 */
    @TableId(type = IdType.AUTO)
    public Long id;
    /** 已核销预约 ID。 */
    public Long bookingId;
    /** 执行核销的员工 ID。 */
    public Long employeeId;
    /** 核销方式。 */
    public VerificationMethod method;
    /** 核销时间。 */
    public Instant verifiedAt;
}

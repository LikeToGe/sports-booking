package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Verification;
import java.util.List;
import com.sportsbooking.admin.base.bo.StaffVerificationRecordBO;

/**
 * 功能描述: 核销记录数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface VerificationMapper extends BaseMapper<Verification> {
    /**
     * 功能描述: 查询指定员工本人的核销记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @param employeeId 员工 ID
     * @return 核销记录列表
     * @author gjm
     */
    List<StaffVerificationRecordBO> selectStaffRecords(long employeeId);
}

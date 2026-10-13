package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.CustomerFollowupVO;
import com.sportsbooking.admin.biz.pojo.vo.CustomerOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueCustomerTagVO;
import java.util.List;

/** 场馆顾客经营摘要、标签和回访待办。 */
public interface CustomerOperationsService {
    /** 查询当前场馆顾客经营摘要，可选固定分组。 */
    List<CustomerOperationVO> listOperations(long venueId, String segment);
    /** 查询当前场馆顾客经营详情。 */
    CustomerOperationVO getOperation(long venueId, long customerId);
    /** 查询当前场馆标签。 */
    List<VenueCustomerTagVO> listTags(long venueId);
    /** 创建标签并记录可信操作者。 */
    VenueCustomerTagVO createTag(long venueId, Long adminId, Long employeeId, String actorName, String name);
    /** 删除标签并保留标签与关联删除审计。 */
    void deleteTag(long venueId, long tagId, Long adminId, Long employeeId, String actorName);
    /** 将当前场馆标签幂等关联到顾客并记录操作者。 */
    void addTag(long venueId, Long adminId, Long employeeId, String actorName, long customerId, long tagId);
    /** 移除标签关联并保留审计。 */
    void removeTag(long venueId, Long adminId, Long employeeId, String actorName, long customerId, long tagId);
    /** 查询当前场馆回访待办。 */
    List<CustomerFollowupVO> listFollowups(long venueId, String status);
    /** 从仍符合条件的本馆分组创建回访待办。 */
    CustomerFollowupVO createFollowup(long venueId, Long adminId, Long employeeId, String actorName,
                                      Long customerId, String purpose, String sourceGroup);
    /** 将待处理回访原子地推进到完成或无需处理。 */
    CustomerFollowupVO updateFollowup(long venueId, Long adminId, Long employeeId, String actorName,
                                      long id, String status, String note);
}

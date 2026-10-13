package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.CustomerFollowupBO;
import com.sportsbooking.admin.base.bo.CustomerOperationBO;
import com.sportsbooking.admin.base.bo.VenueCustomerTagBO;
import com.sportsbooking.admin.base.po.VenueCustomerTag;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 场馆顾客经营、标签和回访待办查询。 */
public interface CustomerOperationsMapper {
    /** 查询当前场馆顾客及其已分开聚合的预约、核销和标签事实。 */
    List<CustomerOperationBO> selectOperations(@Param("venueId") long venueId, @Param("segment") String segment);
    /** 查询当前场馆单个顾客的经营事实。 */
    CustomerOperationBO selectOperation(@Param("venueId") long venueId, @Param("customerId") long customerId);
    /** 查询当前场馆标签。 */
    List<VenueCustomerTagBO> selectTags(@Param("venueId") long venueId);
    /** 查询当前场馆单个标签。 */
    VenueCustomerTagBO selectTag(@Param("venueId") long venueId, @Param("tagId") long tagId);
    /** 创建标签。 */
    int insertTag(VenueCustomerTag tag);
    /** 删除当前场馆标签。 */
    int deleteTag(@Param("venueId") long venueId, @Param("tagId") long tagId);
    /** 查询当前场馆顾客是否存在。 */
    int countCustomer(@Param("venueId") long venueId, @Param("customerId") long customerId);
    /** 查询当前场馆标签是否存在。 */
    int countTag(@Param("venueId") long venueId, @Param("tagId") long tagId);
    /** 查询标签是否已关联当前顾客。 */
    int countTagRelation(@Param("venueId") long venueId, @Param("customerId") long customerId,
                         @Param("tagId") long tagId);
    /** 创建顾客标签关联。 */
    int addTag(@Param("venueId") long venueId, @Param("customerId") long customerId, @Param("tagId") long tagId,
               @Param("createdByAdminId") Long createdByAdminId,
               @Param("createdByEmployeeId") Long createdByEmployeeId,
               @Param("createdByName") String createdByName);
    /** 删除顾客标签关联。 */
    int removeTag(@Param("venueId") long venueId, @Param("customerId") long customerId, @Param("tagId") long tagId);
    /** 将标签删除时的关联写入审计表后移除。 */
    int archiveTagRelations(@Param("venueId") long venueId, @Param("tagId") long tagId,
               @Param("createdByAdminId") Long createdByAdminId,
               @Param("createdByEmployeeId") Long createdByEmployeeId,
               @Param("createdByName") String createdByName);
    /** 删除标签后的顾客标签关系。 */
    int deleteTagRelationsByTag(@Param("venueId") long venueId, @Param("tagId") long tagId);
    /** 写入标签审计事件。 */
    int insertTagAudit(@Param("venueId") long venueId, @Param("customerId") Long customerId,
               @Param("tagId") long tagId, @Param("tagName") String tagName,
               @Param("action") String action, @Param("createdByAdminId") Long createdByAdminId,
               @Param("createdByEmployeeId") Long createdByEmployeeId,
               @Param("createdByName") String createdByName);
    /** 查询当前场馆回访待办。 */
    List<CustomerFollowupBO> selectFollowups(@Param("venueId") long venueId, @Param("status") String status);
    /** 查询当前场馆单个回访待办。 */
    CustomerFollowupBO selectFollowup(@Param("venueId") long venueId, @Param("id") long id);
    /** 查询刚创建的待处理回访待办。 */
    CustomerFollowupBO selectPendingFollowup(@Param("venueId") long venueId,
            @Param("customerId") long customerId, @Param("purpose") String purpose);
    /** 原子地从仍符合分组条件的顾客创建待办。 */
    int insertFollowup(@Param("venueId") long venueId, @Param("customerId") long customerId,
                       @Param("purpose") String purpose, @Param("sourceGroup") String sourceGroup,
                       @Param("createdByAdminId") Long createdByAdminId,
                       @Param("createdByEmployeeId") Long createdByEmployeeId,
                       @Param("createdByName") String createdByName);
    /** 只把待处理状态原子推进到终态。 */
    int updateFollowup(@Param("venueId") long venueId, @Param("id") long id, @Param("status") String status,
                       @Param("note") String note, @Param("handledByAdminId") Long handledByAdminId,
                       @Param("handledByEmployeeId") Long handledByEmployeeId,
                       @Param("handledByName") String handledByName);
}

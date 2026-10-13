package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.CustomerFollowupBO;
import com.sportsbooking.admin.base.bo.CustomerOperationBO;
import com.sportsbooking.admin.base.bo.VenueCustomerTagBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.CustomerOperationsMapper;
import com.sportsbooking.admin.base.po.VenueCustomerTag;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.CustomerFollowupVO;
import com.sportsbooking.admin.biz.pojo.vo.CustomerOperationVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueCustomerTagVO;
import com.sportsbooking.admin.biz.service.CustomerOperationsService;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 顾客经营编辑实时按场馆和当前员工权限边界执行。 */
@Service
public class CustomerOperationsServiceImpl implements CustomerOperationsService {
    private static final String SEGMENT = "30_DAY_NO_REBOOK";
    private static final String PURPOSE = SEGMENT;
    private final CustomerOperationsMapper mapper;

    public CustomerOperationsServiceImpl(CustomerOperationsMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public List<CustomerOperationVO> listOperations(long venueId, String segment) {
        if (segment != null && !segment.isBlank() && !SEGMENT.equals(segment)) {
            throw invalid();
        }
        String selectedSegment = segment == null || segment.isBlank() ? null : segment;
        return mapper.selectOperations(venueId, selectedSegment).stream()
                .map(row -> CustomerOperationVO.from(row, selectedSegment)).toList();
    }

    @Override
    public CustomerOperationVO getOperation(long venueId, long customerId) {
        CustomerOperationBO row = mapper.selectOperation(venueId, customerId);
        if (row == null) {
            throw notFound();
        }
        return CustomerOperationVO.from(row, null);
    }

    @Override
    public List<VenueCustomerTagVO> listTags(long venueId) {
        return mapper.selectTags(venueId).stream().map(VenueCustomerTagVO::from).toList();
    }

    @Override
    @Transactional
    public VenueCustomerTagVO createTag(long venueId, Long adminId, Long employeeId, String actorName, String name) {
        String normalizedName = text(name, 40);
        String normalizedActorName = actorName(actorName);
        validateActor(adminId, employeeId);
        VenueCustomerTag tag = new VenueCustomerTag();
        tag.venueId = venueId;
        tag.name = normalizedName;
        tag.createdByAdminId = adminId;
        tag.createdByEmployeeId = employeeId;
        tag.createdByName = normalizedActorName;
        try {
            mapper.insertTag(tag);
        } catch (DuplicateKeyException exception) {
            throw duplicate();
        }
        mapper.insertTagAudit(venueId, null, tag.id, tag.name, "TAG_CREATED", adminId, employeeId,
                normalizedActorName);
        VenueCustomerTagBO created = mapper.selectTag(venueId, tag.id);
        if (created == null) {
            throw notFound();
        }
        return VenueCustomerTagVO.from(created);
    }

    @Override
    @Transactional
    public void deleteTag(long venueId, long tagId, Long adminId, Long employeeId, String actorName) {
        validateActor(adminId, employeeId);
        String normalizedActorName = actorName(actorName);
        VenueCustomerTagBO tag = mapper.selectTag(venueId, tagId);
        if (tag == null) {
            throw notFound();
        }
        mapper.archiveTagRelations(venueId, tagId, adminId, employeeId, normalizedActorName);
        mapper.deleteTagRelationsByTag(venueId, tagId);
        mapper.insertTagAudit(venueId, null, tagId, tag.getName(), "TAG_DELETED", adminId, employeeId,
                normalizedActorName);
        if (mapper.deleteTag(venueId, tagId) != 1) {
            throw notFound();
        }
    }

    @Override
    @Transactional
    public void addTag(long venueId, Long adminId, Long employeeId, String actorName, long customerId, long tagId) {
        validateActor(adminId, employeeId);
        String normalizedActorName = actorName(actorName);
        VenueCustomerTagBO tag = ensureOwned(venueId, customerId, tagId);
        if (mapper.countTagRelation(venueId, customerId, tagId) > 0) {
            return;
        }
        try {
            mapper.addTag(venueId, customerId, tagId, adminId, employeeId, normalizedActorName);
        } catch (DuplicateKeyException exception) {
            return;
        }
        mapper.insertTagAudit(venueId, customerId, tagId, tag.getName(), "TAG_ASSIGNED", adminId,
                employeeId, normalizedActorName);
    }

    @Override
    @Transactional
    public void removeTag(long venueId, Long adminId, Long employeeId, String actorName,
                          long customerId, long tagId) {
        validateActor(adminId, employeeId);
        String normalizedActorName = actorName(actorName);
        VenueCustomerTagBO tag = ensureOwned(venueId, customerId, tagId);
        if (mapper.removeTag(venueId, customerId, tagId) > 0) {
            mapper.insertTagAudit(venueId, customerId, tagId, tag.getName(), "TAG_UNASSIGNED", adminId,
                    employeeId, normalizedActorName);
        }
    }

    @Override
    public List<CustomerFollowupVO> listFollowups(long venueId, String status) {
        if (status != null && !status.isBlank()
                && !List.of("PENDING", "COMPLETED", "NOT_NEEDED").contains(status)) {
            throw invalid();
        }
        return mapper.selectFollowups(venueId, status).stream().map(CustomerFollowupVO::from).toList();
    }

    @Override
    @Transactional
    public CustomerFollowupVO createFollowup(long venueId, Long adminId, Long employeeId, String actorName,
                                              Long customerId, String purpose, String sourceGroup) {
        if (customerId == null || customerId <= 0) {
            throw invalid();
        }
        validateActor(adminId, employeeId);
        String normalizedActorName = actorName(actorName);
        if (mapper.countCustomer(venueId, customerId) == 0) {
            throw notFound();
        }
        String normalizedPurpose = purpose == null || purpose.isBlank() ? PURPOSE : purpose.trim();
        if (!PURPOSE.equals(normalizedPurpose) || !SEGMENT.equals(sourceGroup)) {
            throw invalid();
        }
        try {
            if (mapper.insertFollowup(venueId, customerId, normalizedPurpose, SEGMENT, adminId, employeeId,
                    normalizedActorName) != 1) {
                throw conflict();
            }
        } catch (DuplicateKeyException exception) {
            throw duplicate();
        }
        CustomerFollowupBO created = mapper.selectPendingFollowup(venueId, customerId, normalizedPurpose);
        if (created == null) {
            throw notFound();
        }
        return CustomerFollowupVO.from(created);
    }

    @Override
    @Transactional
    public CustomerFollowupVO updateFollowup(long venueId, Long adminId, Long employeeId, String actorName,
                                              long id, String status, String note) {
        validateActor(adminId, employeeId);
        String normalizedActorName = actorName(actorName);
        if (!"COMPLETED".equals(status) && !"NOT_NEEDED".equals(status)) {
            throw invalid();
        }
        CustomerFollowupBO row = mapper.selectFollowup(venueId, id);
        if (row == null) {
            throw notFound();
        }
        if (!"PENDING".equals(row.getStatus())) {
            throw conflict();
        }
        String normalizedNote = note == null ? null : note.trim();
        if (normalizedNote != null && normalizedNote.length() > 255) {
            throw invalid();
        }
        if (mapper.updateFollowup(venueId, id, status, normalizedNote, adminId, employeeId,
                normalizedActorName) != 1) {
            throw conflict();
        }
        CustomerFollowupBO updated = mapper.selectFollowup(venueId, id);
        if (updated == null) {
            throw notFound();
        }
        return CustomerFollowupVO.from(updated);
    }

    private VenueCustomerTagBO ensureOwned(long venueId, long customerId, long tagId) {
        if (mapper.countCustomer(venueId, customerId) == 0) {
            throw notFound();
        }
        VenueCustomerTagBO tag = mapper.selectTag(venueId, tagId);
        if (tag == null) {
            throw notFound();
        }
        return tag;
    }

    private String text(String value, int maxLength) {
        if (value == null || value.isBlank() || value.trim().length() > maxLength) {
            throw invalid();
        }
        return value.trim();
    }

    private String actorName(String value) {
        return text(value, 80);
    }

    private void validateActor(Long adminId, Long employeeId) {
        if ((adminId == null) == (employeeId == null)) {
            throw invalid();
        }
    }

    private ApiException invalid() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }

    private ApiException duplicate() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
    }

    private ApiException conflict() {
        return new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS);
    }
}

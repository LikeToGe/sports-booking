package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.ReviewTagSportBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.ReviewTagMapper;
import com.sportsbooking.admin.base.po.ReviewTag;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.ReviewTagRequest;
import com.sportsbooking.admin.biz.pojo.vo.ReviewTagVO;
import com.sportsbooking.admin.biz.service.ReviewTagService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 平台固定评价标签与适用运动类型管理。 */
@Service
public class ReviewTagServiceImpl implements ReviewTagService {
    private final ReviewTagMapper mapper;
    private final boolean ugcEnabled;

    public ReviewTagServiceImpl(ReviewTagMapper mapper, @Value("${app.ugc.enabled:false}") boolean ugcEnabled) {
        this.mapper = mapper;
        this.ugcEnabled = ugcEnabled;
    }

    /** 一次查询标签与关联，平台在 UGC 关闭时仍可维护。 */
    @Override
    public List<ReviewTagVO> listPlatform() {
        Map<Long, List<Long>> sportIdsByTag = new HashMap<>();
        for (ReviewTagSportBO relation : mapper.selectTagSports()) {
            sportIdsByTag.computeIfAbsent(relation.getTagId(), ignored -> new java.util.ArrayList<>())
                    .add(relation.getSportTypeId());
        }
        return mapper.selectAll().stream()
                .map(tag -> ReviewTagVO.from(tag, sportIdsByTag.getOrDefault(tag.id, List.of())))
                .toList();
    }

    /** 公开端仅看当前运动类型仍启用的标签。 */
    @Override
    public List<ReviewTagVO> listPublic(long sportTypeId) {
        if (!ugcEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ErrorCode.UGC_DISABLED);
        }
        return mapper.selectActiveBySport(sportTypeId).stream()
                .map(tag -> ReviewTagVO.from(tag, List.of(sportTypeId))).toList();
    }

    /** 标签名称全平台唯一，适用范围仅接受启用的平台运动类型。 */
    @Override
    @Transactional
    public ReviewTagVO create(ReviewTagRequest request) {
        List<Long> sportTypeIds = validate(request);
        ReviewTag tag = new ReviewTag();
        fill(tag, request);
        tag.createdAt = LocalDateTime.now(ZoneOffset.UTC);
        tag.updatedAt = tag.createdAt;
        try {
            mapper.insert(tag);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        mapper.insertSports(tag.id, sportTypeIds);
        return ReviewTagVO.from(tag, sportTypeIds);
    }

    /** 更新标签不改写已经发布的评价版本所选标签。 */
    @Override
    @Transactional
    public ReviewTagVO update(long tagId, ReviewTagRequest request) {
        List<Long> sportTypeIds = validate(request);
        ReviewTag tag = mapper.selectById(tagId);
        if (tag == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        fill(tag, request);
        tag.updatedAt = LocalDateTime.now(ZoneOffset.UTC);
        try {
            mapper.updateById(tag);
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
        }
        mapper.deleteSports(tagId);
        mapper.insertSports(tagId, sportTypeIds);
        return ReviewTagVO.from(tag, sportTypeIds);
    }

    private List<Long> validate(ReviewTagRequest request) {
        if (request == null || request.getName() == null || request.getName().isBlank()
                || request.getName().trim().length() > 40 || request.getSportTypeIds() == null
                || request.getSportTypeIds().isEmpty() || request.getSportTypeIds().stream()
                .anyMatch(id -> id == null || id <= 0)
                || request.getSportTypeIds().stream().distinct().count() != request.getSportTypeIds().size()
                || (request.getStatus() != null && !List.of("ACTIVE", "DISABLED").contains(request.getStatus()))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        List<Long> sportTypeIds = request.getSportTypeIds();
        if (mapper.selectActivePlatformSportIds(sportTypeIds).size() != sportTypeIds.size()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        return sportTypeIds;
    }

    private void fill(ReviewTag tag, ReviewTagRequest request) {
        tag.name = request.getName().trim();
        tag.normalizedName = tag.name.toLowerCase(Locale.ROOT);
        tag.status = request.getStatus() == null ? "ACTIVE" : request.getStatus();
    }
}

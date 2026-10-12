package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.ReviewTagRequest;
import com.sportsbooking.admin.biz.pojo.vo.ReviewTagVO;
import java.util.List;

/** 平台评价标签维护与场馆公开可选标签。 */
public interface ReviewTagService {
    List<ReviewTagVO> listPlatform();

    List<ReviewTagVO> listPublic(long sportTypeId);

    ReviewTagVO create(ReviewTagRequest request);

    ReviewTagVO update(long tagId, ReviewTagRequest request);
}

package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.RecruitmentDiscoveryVO;
import java.time.LocalDate;
import java.util.List;

/** 按预约事实查询仍可申请的找搭子招募。 */
public interface RecruitmentDiscoveryService {
    List<RecruitmentDiscoveryVO> list(long userId, LocalDate date, Long sportTypeId, Long venueId,
            String levelTag, Integer minRemaining, int page, int size);
}

package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.RecruitmentDiscoveryMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.RecruitmentDiscoveryVO;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import java.time.Instant;
import com.sportsbooking.admin.biz.service.RecruitmentDiscoveryService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 发现结果严格绑定公开动态、确认预约和未开始场次。 */
@Service
public class RecruitmentDiscoveryServiceImpl implements RecruitmentDiscoveryService {
    private static final List<String> LEVEL_TAGS = List.of("BEGINNER_FRIENDLY", "CASUAL", "ADVANCED");
    private final RecruitmentDiscoveryMapper mapper;
    private final ContentGovernanceService governance;
    private final ContentAgreementService agreement;

    public RecruitmentDiscoveryServiceImpl(RecruitmentDiscoveryMapper mapper, ContentGovernanceService governance,
                                           ContentAgreementService agreement) {
        this.mapper = mapper;
        this.governance = governance;
        this.agreement = agreement;
    }

    @Override
    public List<RecruitmentDiscoveryVO> list(long userId, LocalDate date, Long sportTypeId, Long venueId,
            String levelTag, Integer minRemaining, int page, int size) {
        governance.requireAllowed(userId, "RECRUITMENT");
        agreement.requireAccepted(userId);
        if (levelTag != null && !LEVEL_TAGS.contains(levelTag)
                || minRemaining != null && (minRemaining < 1 || minRemaining > 20)
                || page < 1 || size < 1 || size > 50) throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        long offset = ((long) page - 1) * size;
        if (offset > Integer.MAX_VALUE) throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        Instant dayStart = date == null ? null : date.atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        Instant nextDayStart = date == null ? null : date.plusDays(1).atStartOfDay(BusinessConstants.BUSINESS_ZONE).toInstant();
        return mapper.select(userId, dayStart, nextDayStart, sportTypeId, venueId,
                        levelTag, minRemaining, (int) offset, size)
                .stream().map(RecruitmentDiscoveryVO::from).toList();
    }
}

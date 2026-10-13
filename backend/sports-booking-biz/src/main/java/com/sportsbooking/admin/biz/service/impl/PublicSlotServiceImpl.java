package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.mapper.PublicSlotMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.PublicSlotVO;
import com.sportsbooking.admin.biz.service.PublicSlotService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 比较查询沿用预约窗口和完整场次范围，不改变库存或预约快照。 */
@Service
public class PublicSlotServiceImpl implements PublicSlotService {
    private final PublicSlotMapper slotMapper;
    private final VenueMapper venueMapper;

    public PublicSlotServiceImpl(PublicSlotMapper slotMapper, VenueMapper venueMapper) {
        this.slotMapper = slotMapper;
        this.venueMapper = venueMapper;
    }

    @Override
    public List<PublicSlotVO> compare(long venueId, Long sportTypeId, LocalDate date, LocalTime startTime,
                                     LocalTime endTime, Integer minPriceFen, Integer maxPriceFen) {
        if ((startTime == null) != (endTime == null)
                || (startTime != null && (!startTime.isBefore(endTime)
                    || startTime.getSecond() != 0 || endTime.getSecond() != 0
                    || startTime.getNano() != 0 || endTime.getNano() != 0))
                || (minPriceFen != null && minPriceFen < 0) || (maxPriceFen != null && maxPriceFen < 0)
                || (minPriceFen != null && maxPriceFen != null && minPriceFen > maxPriceFen)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null || venue.status != VenueStatus.ACTIVE) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        Instant now = Instant.now();
        LocalDate today = now.atZone(BusinessConstants.BUSINESS_ZONE).toLocalDate();
        LocalDate selected = date == null ? today : date;
        return slotMapper.selectComparison(venueId, sportTypeId, selected, now, today,
                startTime == null ? null : selected.atTime(startTime).atZone(BusinessConstants.BUSINESS_ZONE).toInstant(),
                endTime == null ? null : selected.atTime(endTime).atZone(BusinessConstants.BUSINESS_ZONE).toInstant(),
                minPriceFen, maxPriceFen).stream().map(PublicSlotVO::from).toList();
    }
}

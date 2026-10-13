package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.PublicSlotVO;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** 公开完整场次状态，不读取预约身份。 */
public interface PublicSlotService {
    List<PublicSlotVO> compare(long venueId, Long sportTypeId, LocalDate date, LocalTime startTime,
                              LocalTime endTime, Integer minPriceFen, Integer maxPriceFen);
}

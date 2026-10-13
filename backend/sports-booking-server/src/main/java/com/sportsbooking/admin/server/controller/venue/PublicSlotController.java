package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.biz.pojo.vo.PublicSlotVO;
import com.sportsbooking.admin.biz.service.PublicSlotService;
import com.sportsbooking.admin.server.api.ApiResponse;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 公开只读场次比较入口，仅返回安全状态投影。 */
@RestController
@RequestMapping("/public/venues")
public class PublicSlotController {
    @Autowired
    private PublicSlotService publicSlotService;

    @GetMapping("/{venueId}/slot-comparison")
    public ApiResponse<List<PublicSlotVO>> compare(
            @PathVariable long venueId,
            @RequestParam(required = false) Long sportTypeId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime startTime,
            @RequestParam(required = false) @DateTimeFormat(pattern = "HH:mm") LocalTime endTime,
            @RequestParam(required = false) Integer minPriceFen,
            @RequestParam(required = false) Integer maxPriceFen) {
        return ApiResponse.success(publicSlotService.compare(venueId, sportTypeId, date,
                startTime, endTime, minPriceFen, maxPriceFen));
    }
}

package com.sportsbooking.admin.server.controller.post;

import com.sportsbooking.admin.biz.pojo.vo.RecruitmentDiscoveryVO;
import com.sportsbooking.admin.biz.service.RecruitmentDiscoveryService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import java.time.LocalDate;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 找搭子发现列表，不返回招募成员或预约者隐私。 */
@RestController
@RequestMapping("/mini/recruitments")
public class RecruitmentDiscoveryController {
    @Autowired
    private RecruitmentDiscoveryService service;

    @GetMapping
    public ApiResponse<List<RecruitmentDiscoveryVO>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) Long sportTypeId, @RequestParam(required = false) Long venueId,
            @RequestParam(required = false) String levelTag, @RequestParam(required = false) Integer minRemaining,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.success(service.list(AuthContext.getMiniUserId(), date, sportTypeId, venueId,
                levelTag, minRemaining, page, size));
    }
}

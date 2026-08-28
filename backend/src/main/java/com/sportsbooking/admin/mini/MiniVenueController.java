package com.sportsbooking.admin.mini;

import java.time.LocalDate;
import java.util.Map;
import java.util.List;

import com.sportsbooking.admin.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mini/venues")
public class MiniVenueController {
    private final MiniVenueService miniVenueService;

    public MiniVenueController(MiniVenueService miniVenueService) {
        this.miniVenueService = miniVenueService;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam(required = false) String date, @RequestParam(required = false) Long sportCategoryId,
                                                  @RequestParam(required = false) String keyword, @RequestParam(defaultValue = "1") int page,
                                                  @RequestParam(defaultValue = "20") int pageSize) {
        LocalDate inventoryDate = miniVenueService.parseOrToday(date);
        return ApiResponse.success(miniVenueService.list(inventoryDate, sportCategoryId, keyword, page, pageSize));
    }

    @GetMapping("/{storeId}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("storeId") long storeId, @RequestParam(required = false) String date) {
        return ApiResponse.success(miniVenueService.detail(storeId, miniVenueService.parseOrToday(date)));
    }

    @GetMapping("/sport-categories")
    public ApiResponse<List<Map<String, Object>>> sportCategories(@RequestParam(required = false) String date) {
        return ApiResponse.success(miniVenueService.listSportCategories(miniVenueService.parseOrToday(date)));
    }
}

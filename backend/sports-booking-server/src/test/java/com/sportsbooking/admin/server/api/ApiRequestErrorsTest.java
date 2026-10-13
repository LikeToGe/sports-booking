package com.sportsbooking.admin.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 请求解析失败必须向客户端说明参数错误，不能伪装服务故障。 */
class ApiRequestErrorsTest {
    @Test
    void rejectsFractionalCentsAndMissingRequiredParameterAsBadRequest() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PriceController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/test/price").param("minPriceFen", "50.50"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("参数不正确"));
        mvc.perform(get("/test/price"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("参数不正确"));
    }

    @RestController
    static class PriceController {
        @GetMapping("/test/price")
        ApiResponse<Integer> price(@RequestParam Integer minPriceFen) {
            return ApiResponse.success(minPriceFen);
        }
    }
}

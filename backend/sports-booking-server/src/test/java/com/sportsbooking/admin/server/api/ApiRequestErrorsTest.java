package com.sportsbooking.admin.server.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import com.sportsbooking.admin.biz.pojo.request.ExtraSlotCreateRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
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
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("最低价格（分）必须填写整数"));
        mvc.perform(get("/test/price"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.msg").value("请填写最低价格（分）"));
    }

    @Test
    void identifiesInvalidBodyFieldWithoutEchoingSubmittedValues() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PriceController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(post("/test/slot").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"private-invalid-date\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("场次日期格式应为 yyyy-MM-dd"))
                .andExpect(content().string(not(containsString("private-invalid-date"))));
    }

    @Test
    void preservesSpecificBusinessErrorInResponse() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new PriceController())
                .setControllerAdvice(new ApiExceptionHandler()).build();
        mvc.perform(get("/test/business-error")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.msg").value("所选场地不支持该运动"));
    }

    @RestController
    static class PriceController {
        @PostMapping("/test/slot")
        ApiResponse<ExtraSlotCreateRequest> slot(@RequestBody ExtraSlotCreateRequest body) {
            return ApiResponse.success(body);
        }

        @GetMapping("/test/business-error")
        ApiResponse<Void> businessError() {
            throw new com.sportsbooking.admin.biz.exception.ApiException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    com.sportsbooking.admin.base.enums.ErrorCode.INVALID_ARGUMENT,
                    "所选场地不支持该运动", null);
        }

        @GetMapping("/test/price")
        ApiResponse<Integer> price(@RequestParam Integer minPriceFen) {
            return ApiResponse.success(minPriceFen);
        }
    }
}

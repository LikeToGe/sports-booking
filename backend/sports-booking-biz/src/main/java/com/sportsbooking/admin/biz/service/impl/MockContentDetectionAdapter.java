package com.sportsbooking.admin.biz.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** 本地检测结果模拟；生产检测服务可替换此边界。 */
@Service
public class MockContentDetectionAdapter {
    private final String outcome;

    public MockContentDetectionAdapter(@Value("${app.ugc.mock-detection:PASS}") String outcome) {
        if (!java.util.List.of("PASS", "REVIEW", "RISK", "TIMEOUT", "FAIL").contains(outcome)) {
            throw new IllegalArgumentException("Invalid UGC mock detection outcome");
        }
        this.outcome = outcome;
    }

    public String detect() {
        return switch (outcome) {
            case "PASS" -> "PASSED";
            case "TIMEOUT" -> "TIMED_OUT";
            case "FAIL" -> "FAILED";
            default -> outcome;
        };
    }

    /** 文本检测异常或复核不会因关闭审核开关而放行。 */
    public boolean requiresManualReview() {
        return !"PASSED".equals(detect());
    }
}

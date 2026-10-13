package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.CustomerOperationBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.boot.json.JsonParserFactory;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆顾客经营摘要，不包含跨馆或平台级个人资料。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CustomerOperationVO {
    /** 顾客档案 ID。 */
    @Schema(description = "顾客档案 ID")
    @ApiModelProperty(value = "顾客档案 ID")
    private Long customerId;
    /** 顾客姓名。 */
    @Schema(description = "顾客姓名")
    @ApiModelProperty(value = "顾客姓名")
    private String contactName;
    /** 当前场馆已有顾客档案上的手机号。 */
    @Schema(description = "手机号")
    @ApiModelProperty(value = "手机号")
    private String phone;
    /** 排除待支付和取消后的有效预约次数。 */
    @Schema(description = "有效预约次数，不含待支付和已取消")
    @ApiModelProperty(value = "有效预约次数，不含待支付和已取消")
    private Integer validBookingCount;
    /** 最近一笔有效预约的开始时间。 */
    @Schema(description = "最近有效预约开始时间")
    @ApiModelProperty(value = "最近有效预约开始时间")
    private Instant lastValidBookingAt;
    /** 最近一笔有效预约的来源：ONLINE 或 OFFLINE。 */
    @Schema(description = "最近有效预约来源：ONLINE 或 OFFLINE")
    @ApiModelProperty(value = "最近有效预约来源：ONLINE 或 OFFLINE")
    private String lastValidBookingSource;
    /** 最近一笔已完成线上预约的完成时间。 */
    @Schema(description = "最近完成线上预约时间")
    @ApiModelProperty(value = "最近完成线上预约时间")
    private Instant lastCompletedAt;
    /** 最近一笔已完成线上预约的实际核销时间。 */
    @Schema(description = "最近线上核销时间")
    @ApiModelProperty(value = "最近线上核销时间")
    private Instant latestVerifiedAt;
    /** 当前未来已确认预约数量。 */
    @Schema(description = "未来已确认预约数量")
    @ApiModelProperty(value = "未来已确认预约数量")
    private Integer futureConfirmedCount;
    /** 按预约开始小时聚合的常约次数，格式为小时:次数。 */
    @Schema(description = "常约时段统计，小时:次数")
    @ApiModelProperty(value = "常约时段统计，小时:次数")
    private String usualHours;
    /** 当前场馆顾客标签。 */
    @Schema(description = "当前场馆顾客标签")
    @ApiModelProperty(value = "当前场馆顾客标签")
    private List<String> tags;
    /** 当前筛选分组标识；非分组详情为空。 */
    @Schema(description = "当前筛选分组标识")
    @ApiModelProperty(value = "当前筛选分组标识")
    private String segment;
    /** 顾客在当前场馆建档时间，UTC 时间戳。 */
    @Schema(description = "当前场馆建档时间，UTC 时间戳")
    @ApiModelProperty(value = "当前场馆建档时间，UTC 时间戳")
    private Instant customerCreatedAt;

    /** 从当前场馆顾客投影转换，不读取其他场馆关系或余额。 */
    public static CustomerOperationVO from(CustomerOperationBO source, String segment) {
        // 标签可以包含逗号与引号，数据库 JSON 数组保留名称原文。
        List<String> tags = source.getTagNames() == null || source.getTagNames().isBlank()
                ? List.of() : JsonParserFactory.getJsonParser().parseList(source.getTagNames()).stream()
                        .map(String.class::cast).sorted().toList();
        return new CustomerOperationVO(source.getCustomerId(), source.getContactName(), source.getPhone(),
                source.getValidBookingCount(), source.getLastValidBookingAt(), source.getLastValidBookingSource(),
                source.getLastCompletedAt(), source.getLatestVerifiedAt(), source.getFutureConfirmedCount(),
                source.getUsualHours(), tags, segment, utc(source.getCustomerCreatedAt()));
    }

    private static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 分时段经营分析结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReportUtilizationVO {
    /** 查询开始日期。 */
    @Schema(description = "查询开始日期")
    @ApiModelProperty(value = "查询开始日期")
    private String startDate;
    /** 查询结束日期。 */
    @Schema(description = "查询结束日期")
    @ApiModelProperty(value = "查询结束日期")
    private String endDate;
    /** 业务时区。 */
    @Schema(description = "业务时区")
    @ApiModelProperty(value = "业务时区")
    private String timeZone;
    /** 生成时间。 */
    @Schema(description = "生成时间")
    @ApiModelProperty(value = "生成时间")
    private LocalDateTime generatedAt;
    /** 统计口径说明。 */
    @Schema(description = "统计口径说明")
    @ApiModelProperty(value = "统计口径说明")
    private String methodology;
    /** 星期小时热力图。 */
    @Schema(description = "星期小时热力图")
    @ApiModelProperty(value = "星期小时热力图")
    private List<ReportUtilizationCellVO> heatmap;
    /** 场地对比。 */
    @Schema(description = "场地对比")
    @ApiModelProperty(value = "场地对比")
    private List<ReportCourtPerformanceVO> courts;
    /** 低利用时段。 */
    @Schema(description = "低利用时段")
    @ApiModelProperty(value = "低利用时段")
    private List<ReportLowUtilizationVO> lowUtilization;
    /** 今日及未来供给预订分布，与历史利用率分开。 */
    @Schema(description = "今日及未来供给预订分布，与历史利用率分开")
    @ApiModelProperty(value = "今日及未来供给预订分布，与历史利用率分开")
    private List<ReportUtilizationCellVO> futureHeatmap;
    /** 今日及未来场地供给预订分布。 */
    @Schema(description = "今日及未来场地供给预订分布")
    @ApiModelProperty(value = "今日及未来场地供给预订分布")
    private List<ReportCourtPerformanceVO> futureCourts;
    /** 是否按可信角色返回场地明细。 */
    @Schema(description = "是否按可信角色返回场地明细")
    @ApiModelProperty(value = "是否按可信角色返回场地明细")
    private boolean courtDetailsIncluded;
    /** 本次结果中的运动类型，供报表筛选使用。 */
    @Schema(description = "本次结果中的运动类型")
    @ApiModelProperty(value = "本次结果中的运动类型")
    private java.util.Map<Long, String> sports;
}

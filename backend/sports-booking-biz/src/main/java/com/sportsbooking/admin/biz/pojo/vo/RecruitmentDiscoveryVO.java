package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.RecruitmentDiscoveryBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 找搭子发现卡片，成员信息需进入详情后按原权限读取。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class RecruitmentDiscoveryVO {
    /** 招募 ID。 */
    @Schema(description = "招募 ID") @ApiModelProperty("招募 ID") private Long recruitmentId;
    /** 公开动态 ID。 */
    @Schema(description = "动态 ID") @ApiModelProperty("动态 ID") private Long postId;
    /** 绑定预约的运动类型 ID。 */
    @Schema(description = "运动类型 ID") @ApiModelProperty("运动类型 ID") private Long sportTypeId;
    /** 绑定预约的运动名称。 */
    @Schema(description = "运动类型名称") @ApiModelProperty("运动类型名称") private String sportName;
    /** 绑定预约的场馆 ID。 */
    @Schema(description = "场馆 ID") @ApiModelProperty("场馆 ID") private Long venueId;
    /** 绑定预约的场馆名称。 */
    @Schema(description = "场馆名称") @ApiModelProperty("场馆名称") private String venueName;
    /** 绑定预约开始时间。 */
    @Schema(description = "开始时间") @ApiModelProperty("开始时间") private Instant startAt;
    /** 绑定预约结束时间。 */
    @Schema(description = "结束时间") @ApiModelProperty("结束时间") private Instant endAt;
    /** 招募水平显示文本。 */
    @Schema(description = "水平显示文本") @ApiModelProperty("水平显示文本") private String level;
    /** 有限水平标签；历史记录为空。 */
    @Schema(description = "有限水平标签，历史数据为空") @ApiModelProperty("有限水平标签") private String levelTag;
    /** 招募最大人数。 */
    @Schema(description = "招募容量") @ApiModelProperty("招募容量") private Integer maxMembers;
    /** 已通过人数。 */
    @Schema(description = "已通过人数") @ApiModelProperty("已通过人数") private Integer approvedCount;
    /** 剩余可申请人数。 */
    @Schema(description = "剩余名额") @ApiModelProperty("剩余名额") private Integer remainingCount;
    /** 免费或预计 AA。 */
    @Schema(description = "费用方式") @ApiModelProperty("费用方式") private String feeMode;
    /** 预计 AA 金额，单位分。 */
    @Schema(description = "预计 AA 金额，分") @ApiModelProperty("预计 AA 金额，分") private Integer aaAmountFen;
    /** 招募说明。 */
    @Schema(description = "说明") @ApiModelProperty("说明") private String description;
    /** 公开招募状态。 */
    @Schema(description = "公开状态") @ApiModelProperty("公开状态") private String status;

    public static RecruitmentDiscoveryVO from(RecruitmentDiscoveryBO row) {
        int approved = row.getApprovedCount() == null ? 0 : row.getApprovedCount();
        return new RecruitmentDiscoveryVO(row.getRecruitmentId(), row.getPostId(), row.getSportTypeId(), row.getSportName(),
                row.getVenueId(), row.getVenueName(), row.getStartAt(), row.getEndAt(), row.getLevel(),
                row.getLevelTag(), row.getMaxMembers(), approved, row.getMaxMembers() - approved, row.getFeeMode(),
                row.getAaAmountFen(), row.getDescription(), row.getStatus());
    }
}

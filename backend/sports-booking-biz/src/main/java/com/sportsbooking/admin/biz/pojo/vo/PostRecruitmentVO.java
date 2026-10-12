package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/** 找搭子公开事实与当前用户可见的成员信息。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class PostRecruitmentVO {
    /** 招募 ID。 */
    @Schema(description = "招募 ID") @ApiModelProperty("招募 ID") private Long id;
    /** 动态 ID。 */
    @Schema(description = "动态 ID") @ApiModelProperty("动态 ID") private Long postId;
    /** 水平。 */
    @Schema(description = "水平") @ApiModelProperty("水平") private String level;
    /** 需要人数。 */
    @Schema(description = "需要人数") @ApiModelProperty("需要人数") private Integer maxMembers;
    /** 已加入人数。 */
    @Schema(description = "已加入人数") @ApiModelProperty("已加入人数") private Integer approvedCount;
    /** 费用方式。 */
    @Schema(description = "费用方式") @ApiModelProperty("费用方式") private String feeMode;
    /** 预计 AA 金额。 */
    @Schema(description = "预计 AA 金额，分") @ApiModelProperty("预计 AA 金额，分") private Integer aaAmountFen;
    /** 说明。 */
    @Schema(description = "说明") @ApiModelProperty("说明") private String description;
    /** 招募状态。 */
    @Schema(description = "招募状态") @ApiModelProperty("招募状态") private String status;
    /** 场馆。 */
    @Schema(description = "场馆") @ApiModelProperty("场馆") private String venueName;
    /** 运动。 */
    @Schema(description = "运动") @ApiModelProperty("运动") private String sportName;
    /** 开始时间。 */
    @Schema(description = "开始时间") @ApiModelProperty("开始时间") private Instant startAt;
    /** 结束时间。 */
    @Schema(description = "结束时间") @ApiModelProperty("结束时间") private Instant endAt;
    /** 场地仅返回发起人与已通过成员。 */
    @Schema(description = "受限场地信息") @ApiModelProperty("受限场地信息") private String courtName;
    /** 当前用户是否发起人。 */
    @Schema(description = "是否发起人") @ApiModelProperty("是否发起人") private Boolean isOwner;
    /** 当前用户申请状态。 */
    @Schema(description = "当前用户申请状态") @ApiModelProperty("当前用户申请状态") private String myStatus;
    /** 受限成员列表。 */
    @Schema(description = "成员列表，普通浏览者为空") @ApiModelProperty("成员列表") private List<RecruitmentMemberVO> members;
}

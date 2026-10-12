package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.PostRecruitmentMember;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @NoArgsConstructor @AllArgsConstructor
public class RecruitmentMemberVO {
    /** 成员 ID。 */
    @Schema(description = "成员 ID") @ApiModelProperty("成员 ID") private Long userId;
    /** 参与状态。 */
    @Schema(description = "参与状态") @ApiModelProperty("参与状态") private String status;
    public static RecruitmentMemberVO from(PostRecruitmentMember row) {
        return new RecruitmentMemberVO(row.userId, row.status);
    }
}

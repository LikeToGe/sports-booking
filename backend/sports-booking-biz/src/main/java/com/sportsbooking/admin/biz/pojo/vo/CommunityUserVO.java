package com.sportsbooking.admin.biz.pojo.vo;

import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/** 社区主页不复用预约联系人或会员资料。 */
@Data @NoArgsConstructor @AllArgsConstructor
public class CommunityUserVO {
    /** 用户 ID。 */
    @Schema(description = "用户 ID") @ApiModelProperty("用户 ID") private Long userId;
    /** 社区昵称。 */
    @Schema(description = "社区昵称") @ApiModelProperty("社区昵称") private String nickname;
    /** 社区头像。 */
    @Schema(description = "社区头像") @ApiModelProperty("社区头像") private String avatarUrl;
    /** 公开动态使用的运动类型。 */
    @Schema(description = "运动类型") @ApiModelProperty("运动类型") private List<String> sports;
    /** 正常公开动态。 */
    @Schema(description = "公开动态") @ApiModelProperty("公开动态") private List<PostVO> posts;
}

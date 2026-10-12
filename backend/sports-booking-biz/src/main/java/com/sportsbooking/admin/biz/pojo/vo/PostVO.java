package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.PostBO;
import com.sportsbooking.admin.base.po.Post;
import com.sportsbooking.admin.base.po.PostRevision;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 动态信息流接口模型。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PostVO {
    @Schema(description = "动态 ID") @ApiModelProperty("动态 ID") private Long id;
    @Schema(description = "当前版本 ID") @ApiModelProperty("当前版本 ID") private Long revisionId;
    @Schema(description = "动态正文") @ApiModelProperty("动态正文") private String body;
    @Schema(description = "运动类型 ID") @ApiModelProperty("运动类型 ID") private Long sportTypeId;
    @Schema(description = "运动类型名称") @ApiModelProperty("运动类型名称") private String sportName;
    @Schema(description = "关联场馆 ID") @ApiModelProperty("关联场馆 ID") private Long venueId;
    @Schema(description = "关联场馆名称") @ApiModelProperty("关联场馆名称") private String venueName;
    @Schema(description = "是否官方内容") @ApiModelProperty("是否官方内容") private Boolean isOfficial;
    @Schema(description = "是否置顶") @ApiModelProperty("是否置顶") private Boolean isPinned;
    @Schema(description = "是否允许评论") @ApiModelProperty("是否允许评论") private Boolean commentsEnabled;
    @Schema(description = "当前用户是否为作者") @ApiModelProperty("当前用户是否为作者") private Boolean isMine;
    @Schema(description = "动态状态") @ApiModelProperty("动态状态") private String contentStatus;
    @Schema(description = "当前版本审核状态") @ApiModelProperty("当前版本审核状态") private String moderationStatus;
    @Schema(description = "发布时间") @ApiModelProperty("发布时间") private LocalDateTime createdAt;
    @Schema(description = "当前可见版本媒体") @ApiModelProperty("当前可见版本媒体") private List<UgcMediaVO> media;
    /** 作者 ID。 */
    @Schema(description = "作者 ID") @ApiModelProperty("作者 ID") private Long authorId;
    /** 用户、场馆或平台身份。 */
    @Schema(description = "作者身份") @ApiModelProperty("作者身份") private String authorType;
    /** 社区昵称或官方名称。 */
    @Schema(description = "作者名称") @ApiModelProperty("作者名称") private String authorName;
    /** 点赞数量。 */
    @Schema(description = "点赞数量") @ApiModelProperty("点赞数量") private Long likeCount;
    /** 当前用户是否点赞。 */
    @Schema(description = "是否点赞") @ApiModelProperty("是否点赞") private Boolean liked;
    /** 仅作者可见的预约关联 ID。 */
    @Schema(description = "本人绑定预约 ID，其他用户为空") @ApiModelProperty("本人绑定预约 ID") private Long bookingId;

    public static PostVO from(PostBO row) {
        return new PostVO(row.getId(), row.getRevisionId(), row.getBody(), row.getSportTypeId(), row.getSportName(), row.getVenueId(),
                row.getVenueName(), row.getIsOfficial(), row.getIsPinned(), row.getCommentsEnabled(), row.getIsMine(),
                row.getContentStatus(), row.getModerationStatus(), row.getCreatedAt(), List.of(),
                row.getAuthorId(),row.getAuthorType(),row.getAuthorName(),row.getLikeCount(),Integer.valueOf(1).equals(row.getLiked()),row.getBookingId());
    }

    public static PostVO fromOwn(Post post, PostRevision revision) {
        return new PostVO(post.id, revision.id, revision.body, revision.sportTypeId, null, revision.venueId, null,
                post.isOfficial, post.isPinned, post.commentsEnabled, true, post.contentStatus,
                revision.moderationStatus, post.createdAt, List.of(),post.authorId,post.authorType,null,0L,false,revision.bookingId);
    }
}

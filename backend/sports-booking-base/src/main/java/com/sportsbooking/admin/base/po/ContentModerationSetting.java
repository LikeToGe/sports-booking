package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 平台内容审核开关单行配置。 */
@TableName("s_content_moderation_setting")
public class ContentModerationSetting {
    @TableId
    public Integer id;
    public Boolean postEnabled;
    public Boolean commentEnabled;
    public Boolean reviewEnabled;
    public LocalDateTime updatedAt;
}

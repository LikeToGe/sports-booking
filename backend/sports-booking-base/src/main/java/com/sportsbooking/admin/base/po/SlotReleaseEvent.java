package com.sportsbooking.admin.base.po;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/** 具体场次从占用或关闭恢复为可订的一次稳定事件。 */
@TableName("s_slot_release_event")
public class SlotReleaseEvent {
    @TableId(type = IdType.AUTO)
    public Long id;
    public Long sessionSlotId;
    public String eventKey;
    public LocalDateTime createdAt;
    public LocalDateTime processedAt;
}

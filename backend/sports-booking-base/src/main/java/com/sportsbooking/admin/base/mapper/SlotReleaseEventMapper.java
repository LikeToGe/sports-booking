package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.SlotReleaseEvent;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 具体场次释放事件去重数据访问。 */
public interface SlotReleaseEventMapper extends BaseMapper<SlotReleaseEvent> {
    int insertIdempotent(SlotReleaseEvent event);
    List<Long> selectUnprocessedIds(@Param("limit") int limit);
    int markProcessed(@Param("ids") List<Long> ids);
}

package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.bo.ReviewTagSportBO;
import com.sportsbooking.admin.base.po.ReviewTag;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 固定评价标签及其运动类型范围。 */
public interface ReviewTagMapper extends BaseMapper<ReviewTag> {
    List<ReviewTag> selectAll();

    List<ReviewTag> selectActiveBySport(@Param("sportTypeId") long sportTypeId);

    List<ReviewTagSportBO> selectTagSports();

    List<Long> selectActivePlatformSportIds(@Param("sportTypeIds") List<Long> sportTypeIds);

    int deleteSports(@Param("tagId") long tagId);

    int insertSports(@Param("tagId") long tagId, @Param("sportTypeIds") List<Long> sportTypeIds);
}

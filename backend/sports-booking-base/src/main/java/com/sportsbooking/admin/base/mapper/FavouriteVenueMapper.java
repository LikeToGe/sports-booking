package com.sportsbooking.admin.base.mapper;

import com.sportsbooking.admin.base.bo.FavouriteVenueBO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 用户与场馆收藏关系及最近预约摘要。 */
public interface FavouriteVenueMapper {
    List<FavouriteVenueBO> selectFavourites(@Param("userId") long userId);
    List<FavouriteVenueBO> selectRecent(@Param("userId") long userId);
    /** 幂等新增收藏；除主键重复外的约束错误必须继续抛出。 */
    int insertIdempotent(@Param("userId") long userId, @Param("venueId") long venueId);
    int delete(@Param("userId") long userId, @Param("venueId") long venueId);
    int deleteByUser(@Param("userId") long userId);
}

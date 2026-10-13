package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.vo.FavouriteVenueVO;
import java.util.List;

/** 用户收藏和最近预约场馆入口。 */
public interface FavouriteVenueService {
    /** 查询用户收藏场馆。 */
    List<FavouriteVenueVO> favourites(long userId);
    /** 查询用户最近确认或终态预约场馆。 */
    List<FavouriteVenueVO> recent(long userId);
    /** 收藏指定场馆。 */
    void add(long userId, long venueId);
    /** 取消收藏指定场馆。 */
    void remove(long userId, long venueId);
    /** 清理用户全部收藏。 */
    void clear(long userId);
}

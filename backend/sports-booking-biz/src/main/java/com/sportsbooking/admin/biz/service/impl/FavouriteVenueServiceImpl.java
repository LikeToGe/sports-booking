package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.mapper.FavouriteVenueMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.base.mapper.UserAccountMapper;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.po.UserAccount;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.FavouriteVenueVO;
import com.sportsbooking.admin.biz.service.FavouriteVenueService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 用户收藏和最近预约场馆业务，身份由调用边界传入并始终作为查询条件。 */
@Service
public class FavouriteVenueServiceImpl implements FavouriteVenueService {
    private final FavouriteVenueMapper mapper;
    private final VenueMapper venueMapper;
    private final UserAccountMapper userAccountMapper;

    public FavouriteVenueServiceImpl(FavouriteVenueMapper mapper, VenueMapper venueMapper,
                                     UserAccountMapper userAccountMapper) {
        this.mapper = mapper;
        this.venueMapper = venueMapper;
        this.userAccountMapper = userAccountMapper;
    }

    /** 查询当前用户收藏，已停用场馆仍保留以便明确展示不可预约状态。 */
    @Override
    public List<FavouriteVenueVO> favourites(long userId) {
        return mapper.selectFavourites(userId).stream().map(FavouriteVenueVO::from).toList();
    }

    /** 查询当前用户确认过或已进入终态的最近预约场馆，并按场馆去重。 */
    @Override
    public List<FavouriteVenueVO> recent(long userId) {
        return mapper.selectRecent(userId).stream().map(FavouriteVenueVO::from).toList();
    }

    /** 校验公开场馆后幂等收藏；重复主键由数据库幂等语义处理，其他约束错误继续抛出。 */
    @Override
    @Transactional
    public void add(long userId, long venueId) {
        // 与注销共用用户行锁；先提交的操作决定最终状态，不能在清理偏好后复活收藏。
        UserAccount user = userAccountMapper.selectForUpdate(userId);
        if (user == null || user.status != EnableStatus.ACTIVE || user.deletedAt != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, ErrorCode.USER_DISABLED);
        }
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null || VenueStatus.DRAFT.equals(venue.status)) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        mapper.insertIdempotent(userId, venueId);
    }

    /** 取消当前用户收藏，删除不存在关系时保持幂等成功。 */
    @Override
    public void remove(long userId, long venueId) {
        mapper.delete(userId, venueId);
    }

    /** 注销事务中清理当前用户收藏关系。 */
    @Override
    public void clear(long userId) {
        mapper.deleteByUser(userId);
    }
}

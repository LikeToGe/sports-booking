package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.po.SportType;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.base.mapper.SportTypeMapper;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.pojo.vo.CourtVO;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 场馆运动类型与物理场地业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class VenueCatalogServiceImpl implements VenueCatalogService {
    private final VenueService venueService;
    private final VenueMapper venueMapper;
    private final SportTypeMapper sportTypeMapper;
    private final CourtMapper courtMapper;

    public VenueCatalogServiceImpl(VenueService venueService, VenueMapper venueMapper,
                                   SportTypeMapper sportTypeMapper, CourtMapper courtMapper) {
        this.venueService = venueService;
        this.venueMapper = venueMapper;
        this.sportTypeMapper = sportTypeMapper;
        this.courtMapper = courtMapper;
    }

    /**
     * 功能描述: 查询平台启用的全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<SportType> listGlobalSports() {
        return sportTypeMapper.selectList(new QueryWrapper<SportType>()
                .isNull("owner_venue_id").eq("status", EnableStatus.ACTIVE).orderByAsc("id"));
    }

    /**
     * 功能描述: 查询平台全部全局运动类型，供平台管理端重新启用已停用类型。
     * 创建时间: 2026/09/05
     *
     * @author gjm
     */
    @Override
    public List<SportType> listPlatformSports() {
        return sportTypeMapper.selectList(new QueryWrapper<SportType>()
                .isNull("owner_venue_id").orderByAsc("id"));
    }

    /**
     * 功能描述: 查询平台类型和当前场馆私有运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<SportType> listVenueSports(long venueId) {
        venueService.getVenue(venueId);
        return sportTypeMapper.selectList(new QueryWrapper<SportType>()
                .and(wrapper -> wrapper.isNull("owner_venue_id").or().eq("owner_venue_id", venueId))
                .eq("status", EnableStatus.ACTIVE).orderByAsc("owner_venue_id").orderByAsc("id"));
    }

    /**
     * 功能描述: 创建平台全局运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public SportType createGlobalSport(String name) {
        return createSport(null, name);
    }

    /**
     * 功能描述: 更新平台全局运动类型启停状态。
     * 创建时间: 2026/09/05
     *
     * @author gjm
     */
    @Override
    public SportType updateGlobalSportStatus(long sportTypeId, EnableStatus status) {
        if (status == null) {
            throw badRequest();
        }
        SportType sport = sportTypeMapper.selectById(sportTypeId);
        if (sport == null || sport.ownerVenueId != null) {
            throw notFound();
        }
        // 停用仅限制后续公开展示和场馆配置，不改写已有预约快照。
        sport.status = status;
        sportTypeMapper.updateById(sport);
        return sport;
    }

    /**
     * 功能描述: 创建仅供指定场馆使用的私有运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public SportType createVenueSport(long venueId, String name) {
        venueService.getVenue(venueId);
        return createSport(venueId, name);
    }

    /**
     * 功能描述: 查询指定场馆的物理场地及关联运动。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<CourtVO> listCourts(long venueId) {
        venueService.getVenue(venueId);
        return venueMapper.selectCourtViews(venueId).stream().map(CourtVO::from).toList();
    }

    /**
     * 功能描述: 创建物理场地并保存支持的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Court createCourt(long venueId, String name, List<Long> sportIds) {
        validateSports(venueId, sportIds);
        Court court = new Court();
        court.venueId = venueId;
        court.name = requireText(name);
        court.status = EnableStatus.ACTIVE;
        court.createdAt = LocalDateTime.now();
        court.updatedAt = court.createdAt;
        courtMapper.insert(court);
        courtMapper.replaceSports(court.id, sportIds);
        return court;
    }

    /**
     * 功能描述: 更新物理场地、关联运动和启停状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Court updateCourt(long venueId, long courtId, String name, List<Long> sportIds, EnableStatus status) {
        Court court = requireCourt(venueId, courtId);
        validateSports(venueId, sportIds);
        if (status == null) {
            throw badRequest();
        }
        court.name = requireText(name);
        court.status = status;
        court.updatedAt = LocalDateTime.now();
        courtMapper.updateById(court);
        // 关联表没有附加属性，先清后写使本次请求成为唯一事实。
        courtMapper.deleteSports(court.id);
        courtMapper.replaceSports(court.id, sportIds);
        return court;
    }

    /**
     * 功能描述: 按平台或场馆范围创建运动类型并阻止同名配置。
     * 创建时间: 2026/08/28 16:29
     *
     * @param ownerVenueId 私有类型所属场馆，平台类型为空
     * @param name 运动名称
     * @return 新建运动类型
     * @author gjm
     */
    private SportType createSport(Long ownerVenueId, String name) {
        String displayName = requireText(name);
        String normalizedName = displayName.replaceAll("\\s+", "");
        // 场馆私有类型同时检查全局类型和本场馆类型，其他场馆的同名私有类型不互相影响。
        Long duplicateCount = sportTypeMapper.selectCount(new QueryWrapper<SportType>()
                .eq("normalized_name", normalizedName)
                .and(wrapper -> wrapper.isNull("owner_venue_id")
                        .or(ownerVenueId != null, child -> child.eq("owner_venue_id", ownerVenueId))));
        if (duplicateCount > 0) {
            throw new ApiException(HttpStatus.CONFLICT, ErrorCode.SPORT_EXISTS);
        }
        SportType sport = new SportType();
        sport.ownerVenueId = ownerVenueId;
        sport.name = displayName;
        sport.normalizedName = normalizedName;
        sport.status = EnableStatus.ACTIVE;
        sport.createdAt = LocalDateTime.now();
        sportTypeMapper.insert(sport);
        return sport;
    }

    /**
     * 功能描述: 校验场地归属，阻止跨场馆修改。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param courtId 场地 ID
     * @return 当前场馆场地
     * @author gjm
     */
    private Court requireCourt(long venueId, long courtId) {
        Court court = courtMapper.selectById(courtId);
        if (court == null || court.venueId != venueId) {
            throw new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
        }
        return court;
    }

    /**
     * 功能描述: 校验关联运动均为平台类型或当前场馆私有类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 场馆 ID
     * @param sportIds 运动类型 ID 列表
     * @author gjm
     */
    private void validateSports(long venueId, List<Long> sportIds) {
        if (sportIds == null || sportIds.isEmpty()) {
            throw badRequest();
        }
        Long allowedCount = sportTypeMapper.selectCount(new QueryWrapper<SportType>()
                .in("id", sportIds).eq("status", EnableStatus.ACTIVE)
                .and(wrapper -> wrapper.isNull("owner_venue_id").or().eq("owner_venue_id", venueId)));
        if (allowedCount != sportIds.stream().distinct().count()) {
            throw badRequest();
        }
    }

    /**
     * 功能描述: 校验信任边界上的必填文本。
     * 创建时间: 2026/08/28 16:29
     *
     * @param value 原始文本
     * @return 去除首尾空格的文本
     * @author gjm
     */
    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest();
        }
        return value.trim();
    }

    /**
     * 功能描述: 构造统一参数错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 参数错误异常
     * @author gjm
     */
    private ApiException badRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }

    /**
     * 功能描述: 构造统一资源不存在错误。
     * 创建时间: 2026/09/05
     *
     * @return 资源不存在错误
     * @author gjm
     */
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }
}

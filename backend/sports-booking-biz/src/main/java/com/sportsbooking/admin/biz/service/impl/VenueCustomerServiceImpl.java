package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.bo.VenueCustomerBO;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueCustomerStatus;
import com.sportsbooking.admin.base.mapper.VenueCustomerMapper;
import com.sportsbooking.admin.base.po.VenueCustomer;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.MembershipVO;
import com.sportsbooking.admin.biz.pojo.vo.PlatformCustomerSummaryVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueCustomerVO;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.VenueCustomerService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 功能描述: 场馆顾客建档、识别和会员关系查询业务实现。
 * 创建时间: 2026/09/15
 *
 * @author gjm
 */
@Service
public class VenueCustomerServiceImpl implements VenueCustomerService {
    private static final int MEMBER_CODE_ATTEMPTS = 3;

    private final VenueCustomerMapper venueCustomerMapper;
    private final CredentialService credentialService;
    private final com.sportsbooking.admin.base.mapper.UserAccountMapper users;

    public VenueCustomerServiceImpl(VenueCustomerMapper venueCustomerMapper, CredentialService credentialService,
                                   com.sportsbooking.admin.base.mapper.UserAccountMapper users) {
        this.venueCustomerMapper = venueCustomerMapper;
        this.credentialService = credentialService;
        this.users = users;
    }

    /**
     * 功能描述: 先查后插并由唯一约束兜底，幂等取得同馆用户档案。
     *
     * @param venueId 场馆 ID
     * @param userId 用户 ID
     * @return 唯一顾客档案
     * @author gjm
     */
    @Override
    @Transactional
    public VenueCustomer ensureCustomer(long venueId, long userId) {
        var user = users.selectForUpdate(userId);
        if (user == null || user.status != com.sportsbooking.admin.base.enums.EnableStatus.ACTIVE || user.deletedAt != null) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,ErrorCode.USER_DISABLED);
        }
        VenueCustomer existing = venueCustomerMapper.selectByVenueAndUser(venueId, userId);
        if (existing != null) {
            return existing;
        }

        for (int attempt = 0; attempt < MEMBER_CODE_ATTEMPTS; attempt++) {
            VenueCustomer customer = new VenueCustomer();
            customer.venueId = venueId;
            customer.userId = userId;
            customer.status = VenueCustomerStatus.CUSTOMER;
            customer.memberCode = UUID.randomUUID().toString().replace("-", "")
                    .substring(0, 16).toUpperCase(Locale.ROOT);
            customer.createdAt = LocalDateTime.now();
            customer.updatedAt = customer.createdAt;
            try {
                venueCustomerMapper.insert(customer);
                return customer;
            } catch (DuplicateKeyException exception) {
                // 同馆同用户并发建档时，由数据库唯一约束选出唯一档案并返回胜者。
                existing = venueCustomerMapper.selectByVenueAndUserForUpdate(venueId, userId);
                if (existing != null) {
                    return existing;
                }
            }
        }
        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.DUPLICATE);
    }

    /**
     * 功能描述: 查询当前场馆顾客列表。
     *
     * @param venueId 场馆 ID
     * @param keyword 姓名关键字、完整手机号或会员码
     * @return 顾客列表
     * @author gjm
     */
    @Override
    public List<VenueCustomerVO> listVenueCustomers(long venueId, String keyword) {
        String normalizedKeyword = trimToNull(keyword);
        return venueCustomerMapper.selectVenueCustomers(venueId, normalizedKeyword).stream()
                .map(VenueCustomerVO::from)
                .toList();
    }

    /**
     * 功能描述: 查询当前场馆顾客详情并校验归属。
     *
     * @param venueId 场馆 ID
     * @param customerId 顾客档案 ID
     * @return 顾客详情
     * @author gjm
     */
    @Override
    public VenueCustomerVO getVenueCustomer(long venueId, long customerId) {
        VenueCustomerBO customer = venueCustomerMapper.selectVenueCustomer(venueId, customerId);
        if (customer == null) {
            throw notFound();
        }
        return VenueCustomerVO.from(customer);
    }

    /**
     * 功能描述: 通过会员码或完整手机号识别当前场馆顾客。
     *
     * @param venueId 场馆 ID
     * @param memberCode 可选会员码，与手机号至少提供一项
     * @param phone 可选完整手机号，与会员码至少提供一项
     * @return 识别出的顾客
     * @author gjm
     */
    @Override
    public VenueCustomerVO identifyVenueCustomer(long venueId, String memberCode, String phone) {
        String normalizedCode = trimToNull(memberCode);
        String normalizedPhone = trimToNull(phone);
        if (normalizedCode == null && normalizedPhone == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
        }
        if (normalizedCode != null) {
            normalizedCode = normalizedCode.toUpperCase(Locale.ROOT);
        }
        List<VenueCustomerBO> customers = venueCustomerMapper.selectByIdentity(
                venueId, normalizedCode, normalizedPhone);
        if (customers.size() != 1) {
            throw notFound();
        }
        return VenueCustomerVO.from(customers.get(0));
    }

    /**
     * 功能描述: 查询用户已有场馆关系，列表不批量生成二维码。
     *
     * @param userId 用户 ID
     * @return 场馆关系列表
     * @author gjm
     */
    @Override
    public List<MembershipVO> listMemberships(long userId) {
        return venueCustomerMapper.selectUserMemberships(userId).stream()
                .map(customer -> toMembership(customer, null))
                .toList();
    }

    /**
     * 功能描述: 查询用户在指定场馆的关系并生成会员码二维码。
     *
     * @param userId 用户 ID
     * @param venueId 场馆 ID
     * @return 场馆关系和二维码
     * @author gjm
     */
    @Override
    public MembershipVO getMembership(long userId, long venueId) {
        VenueCustomerBO customer = venueCustomerMapper.selectUserMembership(userId, venueId);
        if (customer == null) {
            throw notFound();
        }
        return toMembership(customer, credentialService.createQrDataUrl(customer.getMemberCode()));
    }

    /**
     * 功能描述: 查询平台场馆级顾客会员数量汇总。
     *
     * @return 各场馆数量汇总，不包含个人字段
     * @author gjm
     */
    @Override
    public List<PlatformCustomerSummaryVO> listPlatformSummaries() {
        return venueCustomerMapper.selectPlatformSummaries().stream()
                .map(PlatformCustomerSummaryVO::from)
                .toList();
    }

    /** 将顾客查询投影转换为小程序会员关系，P1 储值余额固定为空状态。 */
    private MembershipVO toMembership(VenueCustomerBO customer, String qrDataUrl) {
        return new MembershipVO(customer.getVenueId(), customer.getVenueName(), customer.getStatus(),
                customer.getMemberCode(), qrDataUrl, customer.getPaidBalanceFen(), customer.getGiftBalanceFen(),
                customer.getExpiringGiftBalanceFen(), customer.getNextGiftExpiresAt());
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }
}

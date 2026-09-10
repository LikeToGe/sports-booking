package com.sportsbooking.admin.biz.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.AdminAccount;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.mapper.AdminAccountMapper;
import com.sportsbooking.admin.base.mapper.CourtMapper;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.EnableStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VenueStatus;
import com.sportsbooking.admin.biz.pojo.request.VenueSaveRequest;
import com.sportsbooking.admin.base.po.Venue;
import com.sportsbooking.admin.base.mapper.VenueMapper;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.biz.pojo.vo.AvailableSlotVO;
import com.sportsbooking.admin.biz.pojo.vo.CourtVO;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueDetailVO;
import com.sportsbooking.admin.biz.pojo.vo.PublicVenueVO;
import com.sportsbooking.admin.biz.pojo.vo.SportTypeVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueVO;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 功能描述: 场馆资料、平台建档和公开详情业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class VenueServiceImpl implements VenueService {
    private static final int DEFAULT_BOOKING_DAYS = 7;
    private static final int DEFAULT_MIN_BOOKING_MINUTES = 30;
    private static final int DEFAULT_CANCEL_CUTOFF_MINUTES = 120;

    private final VenueMapper venueMapper;
    private final AdminAccountMapper adminAccountMapper;
    private final CourtMapper courtMapper;
    private final BCryptPasswordEncoder passwordEncoder;
    private final Path uploadDirectory;

    public VenueServiceImpl(VenueMapper venueMapper, AdminAccountMapper adminAccountMapper, CourtMapper courtMapper,
                            BCryptPasswordEncoder passwordEncoder,
                            @Value("${app.upload.directory}") String uploadDirectory) {
        this.venueMapper = venueMapper;
        this.adminAccountMapper = adminAccountMapper;
        this.courtMapper = courtMapper;
        this.passwordEncoder = passwordEncoder;
        this.uploadDirectory = Path.of(uploadDirectory).toAbsolutePath().normalize();
    }

    /**
     * 功能描述: 查询平台全部场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<Venue> listPlatformVenues() {
        return venueMapper.selectList(new QueryWrapper<Venue>().orderByDesc("created_at"));
    }

    /**
     * 功能描述: 创建场馆及首个管理员账号。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Venue createVenue(VenueSaveRequest body, String adminUsername, String adminPassword) {
        Venue venue = new Venue();
        applyVenue(venue, body);
        venue.status = VenueStatus.DRAFT;
        venue.createdAt = LocalDateTime.now();
        venue.updatedAt = venue.createdAt;
        venueMapper.insert(venue);

        // 场馆和首个管理员必须在同一事务创建，避免出现无人可维护的场馆。
        AdminAccount admin = new AdminAccount();
        admin.venueId = venue.id;
        admin.role = AdminRole.VENUE_ADMIN;
        admin.username = requireText(adminUsername);
        admin.passwordHash = passwordEncoder.encode(requireText(adminPassword));
        admin.displayName = venue.name + "管理员";
        admin.status = EnableStatus.ACTIVE;
        admin.mustChangePassword = false;
        admin.createdAt = LocalDateTime.now();
        admin.updatedAt = admin.createdAt;
        adminAccountMapper.insert(admin);
        return venue;
    }

    /**
     * 功能描述: 按 ID 查询场馆，不存在时抛出业务异常。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Venue getVenue(long venueId) {
        Venue venue = venueMapper.selectById(venueId);
        if (venue == null) {
            throw notFound();
        }
        return venue;
    }

    /**
     * 功能描述: 更新场馆资料和预约规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Venue updateVenue(long venueId, VenueSaveRequest body) {
        Venue venue = getVenue(venueId);
        applyVenue(venue, body);
        venue.updatedAt = LocalDateTime.now();
        venueMapper.updateById(venue);
        return venue;
    }

    /**
     * 功能描述: 更新场馆上架状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Venue updateVenueStatus(long venueId, VenueStatus status) {
        if (status == null) {
            throw badRequest();
        }
        Venue venue = getVenue(venueId);
        venue.status = status;
        venue.updatedAt = LocalDateTime.now();
        venueMapper.updateById(venue);
        return venue;
    }

    /**
     * 功能描述: 更新场馆服务费率并按基点保存。
     * 创建时间: 2026/09/08
     *
     * @author gjm
     */
    @Override
    public Venue updateServiceFeeRate(long venueId, BigDecimal ratePercent) {
        if (ratePercent == null) {
            throw badRequest();
        }
        int rateBasisPoints;
        try {
            rateBasisPoints = ratePercent.movePointRight(2).intValueExact();
        } catch (ArithmeticException exception) {
            throw badRequest();
        }
        if (rateBasisPoints < 0 || rateBasisPoints > 3000) {
            throw badRequest();
        }
        Venue venue = getVenue(venueId);
        venue.serviceFeeRateBasisPoints = rateBasisPoints;
        venue.updatedAt = LocalDateTime.now();
        venueMapper.updateById(venue);
        return venue;
    }

    /**
     * 功能描述: 保存场馆封面并更新公开访问路径。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public String uploadCover(long venueId, MultipartFile file) {
        Venue venue = getVenue(venueId);
        venue.coverPath = saveImage(file);
        venueMapper.updateById(venue);
        return venue.coverPath;
    }

    /**
     * 功能描述: 保存当前场馆指定场地的主图。
     * 创建时间: 2026/09/04
     *
     * @author gjm
     */
    @Override
    public Court uploadCourtImage(long venueId, long courtId, MultipartFile file) {
        Court court = courtMapper.selectById(courtId);
        if (court == null || court.venueId != venueId) {
            throw notFound();
        }
        court.imagePath = saveImage(file);
        court.updatedAt = LocalDateTime.now();
        courtMapper.updateById(court);
        return court;
    }

    /**
     * 功能描述: 校验图片格式并存入本地上传目录。
     * 创建时间: 2026/09/04
     *
     * @author gjm
     */
    private String saveImage(MultipartFile file) {
        String originalName = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dotIndex = originalName.lastIndexOf('.');
        String extension = dotIndex < 0 ? "" : originalName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!BusinessConstants.COVER_IMAGE_EXTENSIONS.contains(extension)) {
            throw badRequest();
        }
        try {
            Files.createDirectories(uploadDirectory);
            String filename = UUID.randomUUID() + "." + extension;
            Files.copy(file.getInputStream(), uploadDirectory.resolve(filename), StandardCopyOption.REPLACE_EXISTING);
            return "/api/uploads/" + filename;
        } catch (IOException exception) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.UPLOAD_FAILED);
        }
    }

    /**
     * 功能描述: 按名称、运动类型和日期筛选已上架场馆。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<PublicVenueVO> listPublicVenues(String keyword, Long sportTypeId, LocalDate date) {
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        return venueMapper.selectPublicVenues(normalizedKeyword, sportTypeId, date, Instant.now()).stream()
                .map(PublicVenueVO::from).toList();
    }

    /**
     * 功能描述: 查询场馆公开资料和指定日期的开放场次。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public PublicVenueDetailVO getPublicVenue(long venueId, Long sportTypeId, LocalDate date) {
        Venue venue = getVenue(venueId);
        if (venue.status != VenueStatus.ACTIVE) {
            throw notFound();
        }
        LocalDate selectedDate = date == null ? LocalDate.now(BusinessConstants.BUSINESS_ZONE) : date;
        Instant earliest = Instant.now().plusSeconds(venue.minBookingMinutes * 60L);
        return new PublicVenueDetailVO(
                VenueVO.from(venue),
                venueMapper.selectUsedSports(venueId).stream().map(SportTypeVO::from).toList(),
                venueMapper.selectCourtViews(venueId).stream().map(CourtVO::from).toList(),
                venueMapper.selectAvailableSlots(venueId, sportTypeId, selectedDate, earliest).stream()
                        .map(AvailableSlotVO::from).toList()
        );
    }

    /**
     * 功能描述: 将场馆保存请求应用到实体，并集中处理场馆级默认规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private void applyVenue(Venue venue, VenueSaveRequest body) {
        venue.name = requireText(body.getName());
        venue.city = body.getCity() == null || body.getCity().isBlank()
                ? BusinessConstants.DEFAULT_CITY : body.getCity().trim();
        venue.address = requireText(body.getAddress());
        venue.latitude = body.getLatitude();
        venue.longitude = body.getLongitude();
        venue.phone = requireText(body.getPhone());
        venue.facilities = emptyIfNull(body.getFacilities());
        venue.entryNotice = emptyIfNull(body.getEntryNotice());
        venue.bookingDaysAhead = positive(body.getBookingDaysAhead(), DEFAULT_BOOKING_DAYS);
        venue.minBookingMinutes = nonNegative(body.getMinBookingMinutes(), DEFAULT_MIN_BOOKING_MINUTES);
        venue.cancelCutoffMinutes = nonNegative(body.getCancelCutoffMinutes(), DEFAULT_CANCEL_CUTOFF_MINUTES);
    }

    /**
     * 功能描述: 校验必须大于零的场馆规则配置。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private int positive(Integer value, int defaultValue) {
        int result = value == null ? defaultValue : value;
        if (result <= 0) {
            throw badRequest();
        }
        return result;
    }

    /**
     * 功能描述: 校验允许为零的场馆规则配置。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private int nonNegative(Integer value, int defaultValue) {
        int result = value == null ? defaultValue : value;
        if (result < 0) {
            throw badRequest();
        }
        return result;
    }

    /**
     * 功能描述: 校验信任边界上的必填文本。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String requireText(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw badRequest();
        }
        return value.trim();
    }

    /**
     * 功能描述: 将可选文本归一化为空字符串或去空格内容。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private String emptyIfNull(String value) {
        return value == null ? "" : value.trim();
    }

    /**
     * 功能描述: 构造统一参数错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException badRequest() {
        return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT);
    }

    /**
     * 功能描述: 构造统一资源不存在错误。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    private ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND);
    }
}

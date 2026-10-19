package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Verification;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.VerificationMapper;
import com.sportsbooking.admin.biz.service.CredentialService;
import com.sportsbooking.admin.biz.service.VerificationService;
import com.sportsbooking.admin.biz.pojo.vo.StaffVerificationRecordVO;
import com.sportsbooking.admin.biz.constants.BusinessConstants;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VerificationMethod;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.service.EmployeeService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 功能描述: 场馆员工核销与本人核销记录业务实现。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Service
public class VerificationServiceImpl implements VerificationService {
    private final BookingMapper bookingMapper;
    private final VerificationMapper verificationMapper;
    private final CredentialService credentialService;
    private final EmployeeService employeeService;
    private final RedisLockService redisLockService;

    public VerificationServiceImpl(BookingMapper bookingMapper, VerificationMapper verificationMapper,
                                   CredentialService credentialService, EmployeeService employeeService,
                                   RedisLockService redisLockService) {
        this.bookingMapper = bookingMapper;
        this.verificationMapper = verificationMapper;
        this.credentialService = credentialService;
        this.employeeService = employeeService;
        this.redisLockService = redisLockService;
    }

    /**
     * 功能描述: 校验员工身份和核销凭证后完成预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public Booking verify(long staffUserId, String credential, VerificationMethod method) {
        Employee employee = employeeService.getActiveEmployee(staffUserId);
        return verify(employee, credential, method);
    }

    @Override
    public Booking verify(long staffUserId, long venueId, String credential, VerificationMethod method) {
        return verify(employeeService.getActiveEmployee(staffUserId, venueId), credential, method);
    }

    /**
     * 校验可信员工的场馆归属和核销窗口，在同一事务内完成预约与核销记录。
     *
     * @param employee 当前有效的员工身份
     * @param credential 预约二维码或短码
     * @param method 核销方式
     * @return 已完成的预约
     * @author gjm
     */
    private Booking verify(Employee employee, String credential, VerificationMethod method) {
        Booking candidate = resolveCredential(employee.venueId, credential, method);
        return redisLockService.execute(List.of(
                RedisLockType.BOOKING.key(candidate.id), RedisLockType.SESSION_SLOT.key(candidate.sessionSlotId)), () -> {
            Booking booking = bookingMapper.selectForUpdate(candidate.id);
            if (booking == null || !Objects.equals(booking.venueId, employee.venueId)
                    || booking.source != BookingSource.ONLINE
                    || !BookingStatus.CONFIRMED.equals(booking.status)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOT_VERIFIABLE);
            }
            Instant now = Instant.now();
            if (now.isBefore(booking.startAt.minus(BusinessConstants.VERIFICATION_EARLY_WINDOW))
                    || now.isAfter(booking.endAt)) {
                throw new ApiException(HttpStatus.CONFLICT, ErrorCode.OUTSIDE_VERIFY_WINDOW);
            }

            Verification verification = new Verification();
            verification.bookingId = booking.id;
            verification.employeeId = employee.id;
            verification.method = method;
            verification.verifiedAt = now;
            verificationMapper.insert(verification);

            // 核销记录和预约完成状态在同一事务提交，唯一约束使重复核销整体回滚。
            booking.status = BookingStatus.COMPLETED;
            booking.completedAt = now;
            booking.updatedAt = LocalDateTime.now();
            bookingMapper.updateById(booking);
            return booking;
        });
    }

    /**
     * 功能描述: 查询当前员工本人的核销记录。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    public List<StaffVerificationRecordVO> listStaffRecords(long staffUserId) {
        Employee employee = employeeService.getActiveEmployee(staffUserId);
        return verificationMapper.selectStaffRecords(employee.id).stream()
                .map(StaffVerificationRecordVO::from).toList();
    }

    @Override
    public List<StaffVerificationRecordVO> listStaffRecords(long staffUserId, long venueId) {
        Employee employee = employeeService.getActiveEmployee(staffUserId, venueId);
        return verificationMapper.selectStaffRecords(employee.id).stream()
                .map(StaffVerificationRecordVO::from).toList();
    }

    /**
     * 功能描述: 将二维码或短码解析为当前场馆可核销预约；短码多笔命中时拒绝并提示扫码。
     * 创建时间: 2026/08/28 16:29
     *
     * @param venueId 员工所属场馆 ID
     * @param credential 二维码内容或六位短码
     * @param method 核销方式
     * @return 候选预约
     * @author gjm
     */
    private Booking resolveCredential(long venueId, String credential, VerificationMethod method) {
        if (method == VerificationMethod.QR) {
            Long bookingId = credentialService.resolveQr(credential);
            Booking booking = bookingId == null ? null : bookingMapper.selectById(bookingId);
            if (booking != null) {
                if (booking.source != BookingSource.ONLINE) {
                    throw new ApiException(HttpStatus.CONFLICT, ErrorCode.NOT_VERIFIABLE);
                }
                return booking;
            }
        } else if (method == VerificationMethod.SHORT_CODE) {
            // 六位短码可能重复，必须检查整个核销窗口，不能默认核销首笔。
            Instant now = Instant.now();
            Booking matched = null;
            for (Booking booking : bookingMapper.selectVerifiable(
                    venueId, now, now.plus(BusinessConstants.VERIFICATION_EARLY_WINDOW))) {
                if (credentialService.createShortCode(booking.id).equals(credential)) {
                    if (matched != null) {
                        throw new ApiException(HttpStatus.CONFLICT, ErrorCode.INVALID_CREDENTIAL,
                                "核销短码重复，请扫描预约二维码", null);
                    }
                    matched = booking;
                }
            }
            if (matched != null) return matched;
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_METHOD);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CREDENTIAL);
    }
}

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
import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.enums.VerificationMethod;
import com.sportsbooking.admin.base.po.Employee;
import com.sportsbooking.admin.biz.service.EmployeeService;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    public VerificationServiceImpl(BookingMapper bookingMapper, VerificationMapper verificationMapper,
                                   CredentialService credentialService, EmployeeService employeeService) {
        this.bookingMapper = bookingMapper;
        this.verificationMapper = verificationMapper;
        this.credentialService = credentialService;
        this.employeeService = employeeService;
    }

    /**
     * 功能描述: 校验员工身份和核销凭证后完成预约。
     * 创建时间: 2026/08/28 16:29
     *
     * @author gjm
     */
    @Override
    @Transactional
    public Booking verify(long staffUserId, String credential, VerificationMethod method) {
        Employee employee = employeeService.getActiveEmployee(staffUserId);
        Booking candidate = resolveCredential(employee.venueId, credential, method);
        Booking booking = bookingMapper.selectForUpdate(candidate.id);
        if (booking == null || booking.venueId != employee.venueId
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

    /**
     * 功能描述: 将二维码或短码解析为当前场馆可核销预约。
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
                return booking;
            }
        } else if (method == VerificationMethod.SHORT_CODE) {
            // 当前按场馆核销窗口线性匹配；单日预约量过千时再增加短码索引列。
            Instant now = Instant.now();
            for (Booking booking : bookingMapper.selectVerifiable(
                    venueId, now, now.plus(BusinessConstants.VERIFICATION_EARLY_WINDOW))) {
                if (credentialService.createShortCode(booking.id).equals(credential)) {
                    return booking;
                }
            }
        } else {
            throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_METHOD);
        }
        throw new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_CREDENTIAL);
    }
}

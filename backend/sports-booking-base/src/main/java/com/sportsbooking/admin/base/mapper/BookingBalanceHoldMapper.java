package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.BookingBalanceHold;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/** 预约余额冻结数据访问。 */
public interface BookingBalanceHoldMapper extends BaseMapper<BookingBalanceHold> {
    List<BookingBalanceHold> selectByBookingForUpdate(@Param("bookingId") long bookingId);
    List<BookingBalanceHold> selectByBooking(@Param("bookingId") long bookingId);
}

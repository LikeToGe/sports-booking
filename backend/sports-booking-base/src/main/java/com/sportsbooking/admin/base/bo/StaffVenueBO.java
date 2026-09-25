package com.sportsbooking.admin.base.bo;

import lombok.Data;

/** 小程序员工场馆及实时权限聚合结果。 */
@Data
public class StaffVenueBO {
    private Long employeeId;
    private Long venueId;
    private String venueName;
    private String employeeName;
    private String permissions;
}

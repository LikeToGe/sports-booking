package com.sportsbooking.admin.base.bo;

import lombok.Data;

/** 用户常约入口中的安全场馆摘要。 */
@Data
public class FavouriteVenueBO {
    private Long id;
    private String name;
    private String city;
    private String district;
    private String address;
    private String coverPath;
    private String status;
}

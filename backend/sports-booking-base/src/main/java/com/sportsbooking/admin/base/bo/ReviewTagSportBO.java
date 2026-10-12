package com.sportsbooking.admin.base.bo;

import lombok.Data;

/** 固定评价标签与平台运动类型的关联投影。 */
@Data
public class ReviewTagSportBO {
    private Long tagId;
    private Long sportTypeId;
}

package com.sportsbooking.admin.base.bo;

import lombok.Data;

/** 公开评价中固定标签的使用次数。 */
@Data
public class ReviewTagCountBO {
    private Long tagId;
    private String tagName;
    private Long reviewCount;
}

package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.bo.VenueCustomerTagBO;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 场馆顾客标签。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class VenueCustomerTagVO {
    /** 标签 ID。 */
    @Schema(description = "标签 ID")
    @ApiModelProperty(value = "标签 ID")
    private Long id;
    /** 标签名称。 */
    @Schema(description = "标签名称")
    @ApiModelProperty(value = "标签名称")
    private String name;
    /** 创建人姓名快照。 */
    @Schema(description = "创建人姓名快照")
    @ApiModelProperty(value = "创建人姓名快照")
    private String createdByName;
    /** 创建时间，UTC Instant。 */
    @Schema(description = "创建时间，UTC Instant")
    @ApiModelProperty(value = "创建时间，UTC Instant")
    private Instant createdAt;

    /** 转换当前场馆标签投影。 */
    public static VenueCustomerTagVO from(VenueCustomerTagBO source) {
        return new VenueCustomerTagVO(source.getId(), source.getName(), source.getCreatedByName(),
                utc(source.getCreatedAt()));
    }

    private static Instant utc(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}

package com.sportsbooking.admin.biz.pojo.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 功能描述: 场馆封面上传结果。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CoverUploadVO {

    /**
     * 封面访问路径
     */
    @Schema(description = "封面访问路径")
    @ApiModelProperty(value = "封面访问路径")
    private String coverPath;
}

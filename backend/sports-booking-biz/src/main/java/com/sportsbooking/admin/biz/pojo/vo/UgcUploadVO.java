package com.sportsbooking.admin.biz.pojo.vo;

import com.sportsbooking.admin.base.po.UgcUpload;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 用户媒体暂存结果。 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UgcUploadVO {
    /** 上传 ID，供发布版本绑定。 */
    @Schema(description = "上传 ID") @ApiModelProperty("上传 ID") private Long id;
    /** 实际识别的媒体类型。 */
    @Schema(description = "媒体类型") @ApiModelProperty("媒体类型") private String mediaType;
    /** 实际识别的 MIME。 */
    @Schema(description = "MIME") @ApiModelProperty("MIME") private String mimeType;
    /** 实际文件大小。 */
    @Schema(description = "字节数") @ApiModelProperty("字节数") private Long sizeBytes;
    /** 服务端计算的 SHA-256。 */
    @Schema(description = "SHA-256") @ApiModelProperty("SHA-256") private String sha256;
    /** 实际视频时长。 */
    @Schema(description = "视频时长秒数") @ApiModelProperty("视频时长秒数") private Integer durationSeconds;

    public static UgcUploadVO from(UgcUpload upload) {
        return new UgcUploadVO(upload.id, upload.mediaType, upload.mimeType,
                upload.sizeBytes, upload.sha256, upload.durationSeconds);
    }
}

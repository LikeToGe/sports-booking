package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.Court;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 功能描述: 场地及场地运动关联数据访问。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
public interface CourtMapper extends BaseMapper<Court> {
    /**
     * 功能描述: 按 ID 加排他锁查询物理场地。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @return 已锁定场地
     * @author gjm
     */
    Court selectForUpdate(@Param("courtId") long courtId);

    /**
     * 功能描述: 查询场地关联的运动类型 ID。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @return 运动类型 ID 列表
     * @author gjm
     */
    List<Long> selectSportIds(@Param("courtId") long courtId);

    /**
     * 功能描述: 批量写入场地和运动类型关联。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @param sportIds 运动类型 ID 列表
     * @author gjm
     */
    void replaceSports(@Param("courtId") long courtId, @Param("sportIds") List<Long> sportIds);

    /**
     * 功能描述: 删除场地全部运动类型关联。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @author gjm
     */
    void deleteSports(@Param("courtId") long courtId);
}

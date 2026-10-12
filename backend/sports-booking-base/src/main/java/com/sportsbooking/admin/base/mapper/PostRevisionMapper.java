package com.sportsbooking.admin.base.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sportsbooking.admin.base.po.PostRevision;
import org.apache.ibatis.annotations.Param;

/** 动态正文版本持久化。 */
public interface PostRevisionMapper extends BaseMapper<PostRevision> {
    PostRevision selectForPost(@Param("revisionId") long revisionId, @Param("postId") long postId);
}

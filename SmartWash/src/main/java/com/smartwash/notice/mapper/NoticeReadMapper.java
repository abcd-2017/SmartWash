package com.smartwash.notice.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartwash.notice.entity.NoticeRead;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 站内信已读账本 Mapper。
 * 幂等语义：uk_user_notice 唯一索引兜底，重复插入由服务层捕获 DuplicateKeyException 判幂等。
 */
@Mapper
public interface NoticeReadMapper extends BaseMapper<NoticeRead> {

    /**
     * 全平台用户总数（管理端送达统计分母：v1 公告对全部用户可见，无定向推送）。
     */
    long countUsers();

    /**
     * 某条公告的已读人数（分母按 notice 过滤，唯一索引保证一人一条）。
     */
    long countReadByNotice(@Param("noticeId") Long noticeId);
}

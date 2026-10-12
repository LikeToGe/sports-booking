-- 入口来源与预约同事务保存，可关联确认/取消结果；历史预约保留 NULL。
ALTER TABLE s_booking ADD COLUMN entry_type VARCHAR(32) NULL COMMENT '预约入口，不参与计价';

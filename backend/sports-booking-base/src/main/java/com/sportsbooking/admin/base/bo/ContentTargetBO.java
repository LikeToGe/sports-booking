package com.sportsbooking.admin.base.bo;

/** 治理目标归属与当前版本，不含原文。 */
public class ContentTargetBO {
    public Long id;
    public Long ownerUserId;
    public String contentStatus;
    public Long currentRevisionId;
    public Long publicRevisionId;
}

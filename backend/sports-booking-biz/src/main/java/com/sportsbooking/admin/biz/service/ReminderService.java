package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.biz.pojo.request.ReminderSubscriptionRequest;
import com.sportsbooking.admin.base.po.ReminderSubscription;
import java.util.List;
import com.sportsbooking.admin.biz.pojo.vo.ReminderSubscriptionVO;

/** 到场、开售和空位提醒的登记及调度边界。 */
public interface ReminderService {
    ReminderSubscription subscribe(long userId, ReminderSubscriptionRequest request);
    List<ReminderSubscriptionVO> listSubscriptions(long userId);
    void cancelSubscription(long userId, long subscriptionId);
    /** 仅记录一次真实释放事件；releaseKey 是稳定的预约或人工操作业务键。 */
    void onSlotReleased(long sessionSlotId, String releaseKey);
    /** 预约确认提交后，临近开始时即时补记到场消息。 */
    void generateArrivalIfDue(long bookingId);
    void runReminderJobs();
}

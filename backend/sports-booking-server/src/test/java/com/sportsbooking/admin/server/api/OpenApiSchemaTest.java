package com.sportsbooking.admin.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.biz.pojo.request.BookingCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingBatchCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.OfflineBookingRescheduleRequest;
import com.sportsbooking.admin.biz.pojo.request.AdminRechargeRequest;
import com.sportsbooking.admin.biz.pojo.request.MiniRechargeCreateRequest;
import com.sportsbooking.admin.biz.pojo.request.MockFundsNotificationRequest;
import com.sportsbooking.admin.biz.pojo.request.RechargePlanRequest;
import io.swagger.v3.core.converter.ModelConverters;
import io.swagger.v3.oas.models.media.Schema;
import org.junit.jupiter.api.Test;

class OpenApiSchemaTest {

    @Test
    void documentsBookingCreateRequestFields() {
        Schema<?> schema = ModelConverters.getInstance().read(BookingCreateRequest.class).get("BookingCreateRequest");

        assertThat(schema.getProperties().get("sessionSlotId").getDescription()).isEqualTo("具体场次 ID");
        assertThat(schema.getProperties().get("contactName").getDescription()).isEqualTo("联系人姓名");
        assertThat(schema.getProperties().get("contactPhone").getDescription()).isEqualTo("联系人手机号");
    }

    @Test
    void documentsP1OfflineBookingRequestFields() {
        Schema<?> offline = schema(OfflineBookingCreateRequest.class);
        Schema<?> batch = schema(OfflineBookingBatchCreateRequest.class);
        Schema<?> reschedule = schema(OfflineBookingRescheduleRequest.class);

        assertThat(offline.getProperties()).containsKeys(
                "sessionSlotId", "contactName", "contactPhone", "paymentStatus", "venueCustomerId");
        assertThat(batch.getProperties()).containsKeys("sessionSlotId", "dates", "paymentStatus");
        assertThat(reschedule.getProperties()).containsKeys("sessionSlotId", "reason");
    }

    @Test
    void documentsP2FundsRequestFields() {
        assertThat(schema(RechargePlanRequest.class).getProperties()).containsKeys(
                "name", "paidAmountFen", "giftAmountFen", "giftValidDays", "status");
        assertThat(schema(AdminRechargeRequest.class).getProperties()).containsKeys(
                "planId", "channel", "idempotencyKey");
        assertThat(schema(MiniRechargeCreateRequest.class).getProperties()).containsKeys(
                "venueId", "planId", "idempotencyKey");
        assertThat(schema(MockFundsNotificationRequest.class).getProperties()).containsKeys(
                "businessNo", "amountFen", "result", "signature");
    }

    private Schema<?> schema(Class<?> type) {
        return ModelConverters.getInstance().read(type).get(type.getSimpleName());
    }
}

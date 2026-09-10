package com.sportsbooking.admin.server.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.biz.pojo.request.BookingCreateRequest;
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
}

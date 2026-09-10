package com.sportsbooking.admin.server.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.server.controller.auth.AuthController;
import com.sportsbooking.admin.server.controller.booking.MiniBookingController;
import com.sportsbooking.admin.server.controller.booking.StaffVerificationController;
import com.sportsbooking.admin.server.controller.booking.VenueBookingController;
import com.sportsbooking.admin.server.controller.schedule.ScheduleController;
import com.sportsbooking.admin.server.controller.venue.MiniVenueController;
import com.sportsbooking.admin.server.controller.venue.PlatformVenueController;
import com.sportsbooking.admin.server.controller.venue.VenueAdminController;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

class ControllerPathTest {

    @Test
    void controllerPathsAreExplicitAndUnique() {
        List<Class<?>> controllers = List.of(
                AuthController.class,
                MiniBookingController.class,
                StaffVerificationController.class,
                VenueBookingController.class,
                ScheduleController.class,
                MiniVenueController.class,
                PlatformVenueController.class,
                VenueAdminController.class
        );
        Map<String, String> pathOwners = new HashMap<>();

        for (Class<?> controller : controllers) {
            RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controller, RequestMapping.class);
            assertThat(classMapping).isNotNull();
            assertThat(classMapping.value()).hasSize(1);

            for (Method method : controller.getDeclaredMethods()) {
                RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
                if (methodMapping == null) {
                    continue;
                }
                assertThat(methodMapping.value()).as(method.toString()).hasSize(1);
                assertThat(methodMapping.value()[0]).as(method.toString()).isNotBlank();

                String fullPath = classMapping.value()[0] + methodMapping.value()[0];
                String owner = controller.getSimpleName() + "." + method.getName();
                assertThat(pathOwners.putIfAbsent(fullPath, owner))
                        .as("路径 %s 与 %s 重复", fullPath, owner)
                        .isNull();
            }
        }
    }
}

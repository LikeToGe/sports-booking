package com.sportsbooking.admin.server.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.sportsbooking.admin.base.enums.AdminRole;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.server.context.AuthContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class PrincipalsTest {
    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Test
    void venuePasswordAdministratorBypassesCustomRolePermissions() {
        AdminPrincipal principal = new AdminPrincipal(1L, AdminRole.VENUE_ADMIN, 2L, "超级管理员");
        AuthContext.setAdminPrincipal(principal);

        assertThat(Principals.venue(VenuePermission.ROLE_MANAGE)).isSameAs(principal);
        assertThat(Principals.venueAny(VenuePermission.REPORT_EXPORT)).isSameAs(principal);
    }
}

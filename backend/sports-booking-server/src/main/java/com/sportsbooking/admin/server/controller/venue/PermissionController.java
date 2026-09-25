package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.biz.pojo.request.EmployeeRolesRequest;
import com.sportsbooking.admin.biz.pojo.request.RoleSaveRequest;
import com.sportsbooking.admin.biz.pojo.request.WebLoginConfirmRequest;
import com.sportsbooking.admin.biz.pojo.vo.PermissionCatalogVO;
import com.sportsbooking.admin.biz.pojo.vo.StaffVenueVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueRoleVO;
import com.sportsbooking.admin.biz.service.PermissionService;
import com.sportsbooking.admin.biz.service.WebLoginTicketService;
import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.server.context.AuthContext;
import com.sportsbooking.admin.server.support.Principals;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 场馆角色、员工授权与小程序员工场馆入口。 */
@RestController
@RequestMapping("/")
public class PermissionController {
    @Autowired
    private PermissionService permissionService;
    @Autowired
    private WebLoginTicketService webLoginTicketService;

    @GetMapping("venue-admin/permissions")
    public ApiResponse<List<PermissionCatalogVO>> listPermissions() {
        Principals.venue(VenuePermission.ROLE_MANAGE);
        return ApiResponse.success(permissionService.listPermissions());
    }

    @GetMapping("venue-admin/roles")
    public ApiResponse<List<VenueRoleVO>> listRoles() {
        return ApiResponse.success(permissionService.listRoles(roleManager().getVenueId()));
    }

    @PostMapping("venue-admin/roles")
    public ApiResponse<VenueRoleVO> createRole(@RequestBody RoleSaveRequest request) {
        return ApiResponse.success(permissionService.createRole(roleManager().getVenueId(), request));
    }

    @PutMapping("venue-admin/roles/{roleId}")
    public ApiResponse<VenueRoleVO> updateRole(@PathVariable long roleId, @RequestBody RoleSaveRequest request) {
        return ApiResponse.success(permissionService.updateRole(roleManager().getVenueId(), roleId, request));
    }

    @DeleteMapping("venue-admin/roles/{roleId}")
    public ApiResponse<Boolean> deleteRole(@PathVariable long roleId) {
        permissionService.deleteRole(roleManager().getVenueId(), roleId);
        return ApiResponse.success(true);
    }

    @GetMapping("venue-admin/employees/{employeeId}/roles")
    public ApiResponse<List<Long>> listEmployeeRoles(@PathVariable long employeeId) {
        return ApiResponse.success(permissionService.listEmployeeRoleIds(roleManager().getVenueId(), employeeId));
    }

    @PutMapping("venue-admin/employees/{employeeId}/roles")
    public ApiResponse<Boolean> replaceEmployeeRoles(
            @PathVariable long employeeId, @RequestBody EmployeeRolesRequest request) {
        permissionService.replaceEmployeeRoles(roleManager().getVenueId(), employeeId, request.getRoleIds());
        return ApiResponse.success(true);
    }

    @GetMapping("staff/venues")
    public ApiResponse<List<StaffVenueVO>> listStaffVenues() {
        return ApiResponse.success(permissionService.listStaffVenues(AuthContext.getMiniUserId()));
    }

    @PostMapping("staff/web-login-tickets/{ticket}/confirm")
    public ApiResponse<Boolean> confirmWebLoginTicket(
            @PathVariable String ticket, @RequestBody WebLoginConfirmRequest request) {
        webLoginTicketService.confirm(ticket, AuthContext.getMiniUserId(), request.getVenueId());
        return ApiResponse.success(true);
    }

    private AdminPrincipal roleManager() {
        return Principals.venue(VenuePermission.ROLE_MANAGE);
    }
}

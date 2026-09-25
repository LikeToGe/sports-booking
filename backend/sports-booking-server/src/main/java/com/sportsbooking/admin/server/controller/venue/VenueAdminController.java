package com.sportsbooking.admin.server.controller.venue;

import com.sportsbooking.admin.server.api.ApiResponse;
import com.sportsbooking.admin.biz.pojo.request.CourtSaveRequest;
import com.sportsbooking.admin.biz.pojo.request.EnableStatusUpdateRequest;
import com.sportsbooking.admin.biz.pojo.request.NameRequest;
import com.sportsbooking.admin.biz.pojo.request.VenueSaveRequest;
import com.sportsbooking.admin.base.po.Court;
import com.sportsbooking.admin.base.model.AdminPrincipal;
import com.sportsbooking.admin.base.enums.VenuePermission;
import com.sportsbooking.admin.biz.service.EmployeeService;
import com.sportsbooking.admin.biz.service.VenueCatalogService;
import com.sportsbooking.admin.biz.service.VenueService;
import com.sportsbooking.admin.server.support.Principals;
import com.sportsbooking.admin.biz.pojo.vo.CourtVO;
import com.sportsbooking.admin.biz.pojo.vo.CoverUploadVO;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeBindingVO;
import com.sportsbooking.admin.biz.pojo.vo.EmployeeVO;
import com.sportsbooking.admin.biz.pojo.vo.SportTypeVO;
import com.sportsbooking.admin.biz.pojo.vo.VenueVO;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 功能描述: 场馆管理员维护资料、场地、私有运动类型和员工的协议入口。
 * 创建时间: 2026/08/28 16:29
 *
 * @author gjm
 */
@RestController
@RequestMapping("/venue-admin")
public class VenueAdminController {
    @Autowired
    private VenueService venueService;
    @Autowired
    private VenueCatalogService venueCatalogService;
    @Autowired
    private EmployeeService employeeService;

    /**
     * 功能描述: 查询当前场馆资料和预约规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 场馆资料
     * @author gjm
    */
    @GetMapping("/profile/detail")
    public ApiResponse<VenueVO> getProfile() {
        return ApiResponse.success(VenueVO.from(venueService.getVenue(
                venuePrincipal(VenuePermission.VENUE_PROFILE_VIEW).getVenueId())));
    }

    /**
     * 功能描述: 更新当前场馆资料和预约规则。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 场馆保存请求
     * @return 更新后的场馆资料
     * @author gjm
    */
    @PutMapping("/profile/update")
    public ApiResponse<VenueVO> updateProfile(@RequestBody VenueSaveRequest body) {
        return ApiResponse.success(VenueVO.from(venueService.updateVenue(
                venuePrincipal(VenuePermission.VENUE_PROFILE_EDIT).getVenueId(), body)));
    }

    /**
     * 功能描述: 上传当前场馆封面。
     * 创建时间: 2026/08/28 16:29
     *
     * @param file 封面图片
     * @return 封面公开路径
     * @author gjm
    */
    @PostMapping("/profile/cover")
    public ApiResponse<CoverUploadVO> uploadCover(@RequestParam MultipartFile file) {
        String coverPath = venueService.uploadCover(
                venuePrincipal(VenuePermission.VENUE_PROFILE_EDIT).getVenueId(), file);
        return ApiResponse.success(new CoverUploadVO(coverPath));
    }

    /**
     * 功能描述: 查询当前场馆可使用的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 可用运动类型
     * @author gjm
    */
    @GetMapping("/sports/list")
    public ApiResponse<List<SportTypeVO>> listSports() {
        return ApiResponse.success(venueCatalogService.listVenueSports(
                venuePrincipal(VenuePermission.COURT_VIEW).getVenueId()).stream()
                .map(SportTypeVO::from).toList());
    }

    /**
     * 功能描述: 创建仅属于当前场馆的运动类型。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 运动名称请求
     * @return 新建运动类型
     * @author gjm
    */
    @PostMapping("/sports/create")
    public ApiResponse<SportTypeVO> createSport(@RequestBody NameRequest body) {
        return ApiResponse.success(SportTypeVO.from(
                venueCatalogService.createVenueSport(
                        venuePrincipal(VenuePermission.COURT_EDIT).getVenueId(), body.getName())));
    }

    /**
     * 功能描述: 更新当前场馆私有运动类型名称。
     * 创建时间: 2026/09/11
     *
     * @param sportTypeId 运动类型 ID
     * @param body 新名称请求
     * @return 更新后的运动类型
     * @author gjm
     */
    @PutMapping("/sports/{sportTypeId}")
    public ApiResponse<SportTypeVO> updateSportName(
            @PathVariable long sportTypeId, @RequestBody NameRequest body) {
        return ApiResponse.success(SportTypeVO.from(venueCatalogService.updateVenueSportName(
                venuePrincipal(VenuePermission.COURT_EDIT).getVenueId(), sportTypeId, body.getName())));
    }

    /**
     * 功能描述: 查询当前场馆物理场地。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 场地列表
     * @author gjm
    */
    @GetMapping("/courts/list")
    public ApiResponse<List<CourtVO>> listCourts() {
        return ApiResponse.success(venueCatalogService.listCourts(
                venuePrincipal(VenuePermission.COURT_VIEW).getVenueId()));
    }

    /**
     * 功能描述: 创建当前场馆物理场地。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 场地保存请求
     * @return 新建场地
     * @author gjm
    */
    @PostMapping("/courts/create")
    public ApiResponse<CourtVO> createCourt(@RequestBody CourtSaveRequest body) {
        long venueId = venuePrincipal(VenuePermission.COURT_EDIT).getVenueId();
        Court court = venueCatalogService.createCourt(venueId, body.getName(), body.getSportIds());
        return ApiResponse.success(new CourtVO(court.id, court.name, court.imagePath, court.status, null,
                body.getSportIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))));
    }

    /**
     * 功能描述: 更新当前场馆物理场地。
     * 创建时间: 2026/08/28 16:29
     *
     * @param courtId 场地 ID
     * @param body 场地保存请求
     * @return 更新后的场地
     * @author gjm
    */
    @PutMapping("/courts/{courtId}")
    public ApiResponse<CourtVO> updateCourt(@PathVariable long courtId, @RequestBody CourtSaveRequest body) {
        Court court = venueCatalogService.updateCourt(
                venuePrincipal(VenuePermission.COURT_EDIT).getVenueId(), courtId,
                body.getName(), body.getSportIds(), body.getStatus());
        return ApiResponse.success(new CourtVO(court.id, court.name, court.imagePath, court.status, null,
                body.getSportIds().stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(","))));
    }

    /**
     * 功能描述: 上传当前场馆指定场地的主图。
     * 创建时间: 2026/09/04
     *
     * @param courtId 场地 ID
     * @param file 场地图片
     * @return 更新后的场地
     * @author gjm
     */
    @PostMapping("/courts/{courtId}/image")
    public ApiResponse<CourtVO> uploadCourtImage(@PathVariable long courtId, @RequestParam MultipartFile file) {
        Court court = venueService.uploadCourtImage(
                venuePrincipal(VenuePermission.COURT_EDIT).getVenueId(), courtId, file);
        return ApiResponse.success(new CourtVO(court.id, court.name, court.imagePath, court.status, null, null));
    }

    /**
     * 功能描述: 查询当前场馆员工。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 不含绑定认证字段的员工列表
     * @author gjm
    */
    @GetMapping("/employees/list")
    public ApiResponse<List<EmployeeVO>> listEmployees() {
        return ApiResponse.success(employeeService.listEmployees(
                venuePrincipal(VenuePermission.EMPLOYEE_VIEW).getVenueId()).stream()
                .map(EmployeeVO::from).toList());
    }

    /**
     * 功能描述: 创建员工并签发首次绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @param body 员工姓名请求
     * @return 一次性绑定码
     * @author gjm
    */
    @PostMapping("/employees/create")
    public ApiResponse<EmployeeBindingVO> createEmployee(@RequestBody NameRequest body) {
        return ApiResponse.success(employeeService.createEmployee(
                venuePrincipal(VenuePermission.EMPLOYEE_EDIT).getVenueId(), body.getName()));
    }

    /**
     * 功能描述: 更新当前场馆员工姓名。
     * 创建时间: 2026/09/11
     *
     * @param employeeId 员工 ID
     * @param body 新姓名请求
     * @return 更新后的员工
     * @author gjm
     */
    @PutMapping("/employees/{employeeId}")
    public ApiResponse<EmployeeVO> updateEmployeeName(
            @PathVariable long employeeId, @RequestBody NameRequest body) {
        return ApiResponse.success(EmployeeVO.from(employeeService.updateEmployeeName(
                venuePrincipal(VenuePermission.EMPLOYEE_EDIT).getVenueId(), employeeId, body.getName())));
    }

    /**
     * 功能描述: 为员工重新签发一次性绑定码。
     * 创建时间: 2026/08/28 16:29
     *
     * @param employeeId 员工 ID
     * @return 一次性绑定码
     * @author gjm
    */
    @PostMapping("/employees/{employeeId}/binding-code")
    public ApiResponse<EmployeeBindingVO> issueBindingCode(@PathVariable long employeeId) {
        return ApiResponse.success(employeeService.issueBindingCode(
                venuePrincipal(VenuePermission.EMPLOYEE_EDIT).getVenueId(), employeeId
        ));
    }

    /**
     * 功能描述: 更新当前场馆员工启停状态。
     * 创建时间: 2026/08/28 16:29
     *
     * @param employeeId 员工 ID
     * @param body 状态更新请求
     * @return 更新后的员工
     * @author gjm
    */
    @PatchMapping("/employees/{employeeId}/status")
    public ApiResponse<EmployeeVO> updateEmployeeStatus(
            @PathVariable long employeeId, @RequestBody EnableStatusUpdateRequest body) {
        return ApiResponse.success(EmployeeVO.from(employeeService.updateEmployeeStatus(
                venuePrincipal(VenuePermission.EMPLOYEE_EDIT).getVenueId(), employeeId, body.getStatus()
        )));
    }

    @DeleteMapping("/employees/{employeeId}/binding")
    public ApiResponse<EmployeeVO> unbindEmployee(@PathVariable long employeeId) {
        return ApiResponse.success(EmployeeVO.from(employeeService.unbindEmployee(
                venuePrincipal(VenuePermission.EMPLOYEE_EDIT).getVenueId(), employeeId)));
    }

    /**
     * 功能描述: 从服务端认证上下文获取场馆范围，不接受客户端传入 venueId。
     * 创建时间: 2026/08/28 16:29
     *
     * @return 当前场馆管理员身份
     * @author gjm
     */
    private AdminPrincipal venuePrincipal(VenuePermission permission) {
        return Principals.venue(permission);
    }
}

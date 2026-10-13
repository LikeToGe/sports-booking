package com.sportsbooking.admin.biz.service;

import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.UserActionEventMapper;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.vo.UserActionEventVO;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/** 客户端只记录导航事实；业务结果由对应事务主动写入。 */
@Service
public class UserActionEventService {
    private final UserActionEventMapper mapper;
    public UserActionEventService(UserActionEventMapper mapper) { this.mapper = mapper; }

    public void navigation(long userId, String eventType, String targetType, long targetId) {
        if (eventType == null || targetType == null || targetId <= 0) throw invalid();
        boolean valid = switch (eventType) {
            case "VENUE_TO_BOOKING", "POST_TO_VENUE" -> "VENUE".equals(targetType);
            case "POST_TO_BOOKING", "RECRUITMENT_DISCOVERY_OPEN" -> "POST".equals(targetType);
            case "SHARE_OPEN" -> List.of("POST","VENUE").contains(targetType);
            default -> false;
        };
        if (!valid) throw invalid();
        mapper.insert(userId,eventType,targetType,targetId);
    }

    public void fact(long userId, String eventType, String targetType, long targetId) {
        mapper.insert(userId,eventType,targetType,targetId);
    }

    public List<UserActionEventVO> recent() {
        return mapper.selectRecent().stream().map(UserActionEventVO::from).toList();
    }

    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST,ErrorCode.INVALID_ARGUMENT); }
}

package com.sportsbooking.admin.biz.service.impl;

import com.sportsbooking.admin.base.enums.BookingStatus;
import com.sportsbooking.admin.base.enums.BookingSource;
import com.sportsbooking.admin.base.enums.ErrorCode;
import com.sportsbooking.admin.base.mapper.BookingMapper;
import com.sportsbooking.admin.base.mapper.PostMapper;
import com.sportsbooking.admin.base.mapper.PostRevisionMapper;
import com.sportsbooking.admin.base.mapper.PostRecruitmentMapper;
import com.sportsbooking.admin.base.mapper.PostInteractionMapper;
import com.sportsbooking.admin.base.po.Booking;
import com.sportsbooking.admin.base.po.Post;
import com.sportsbooking.admin.base.po.PostRevision;
import com.sportsbooking.admin.base.po.PostRecruitment;
import com.sportsbooking.admin.base.po.PostRecruitmentMember;
import com.sportsbooking.admin.biz.constants.RedisLockType;
import com.sportsbooking.admin.biz.exception.ApiException;
import com.sportsbooking.admin.biz.pojo.request.PostRecruitmentRequest;
import com.sportsbooking.admin.biz.pojo.vo.PostRecruitmentVO;
import com.sportsbooking.admin.biz.pojo.vo.RecruitmentMemberVO;
import com.sportsbooking.admin.biz.service.ContentAgreementService;
import com.sportsbooking.admin.biz.service.ContentGovernanceService;
import com.sportsbooking.admin.biz.service.ContentMessageService;
import com.sportsbooking.admin.biz.service.PostRecruitmentService;
import com.sportsbooking.admin.biz.service.RedisLockService;
import com.sportsbooking.admin.biz.service.UserActionEventService;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/** 按动态资源加招募锁；内容互动只读取预约事实，不改动资金或履约。 */
@Service
public class PostRecruitmentServiceImpl implements PostRecruitmentService {
    private static final List<String> LEVEL_TAGS = List.of("BEGINNER_FRIENDLY", "CASUAL", "ADVANCED");
    private final PostMapper posts;
    private final PostRevisionMapper revisions;
    private final BookingMapper bookings;
    private final PostRecruitmentMapper mapper;
    private final PostInteractionMapper interactions;
    private final ContentAgreementService agreement;
    private final ContentGovernanceService governance;
    private final RedisLockService locks;
    private final ContentMessageService messages;
    private final UserActionEventService events;

    public PostRecruitmentServiceImpl(PostMapper posts, PostRevisionMapper revisions, BookingMapper bookings,
            PostRecruitmentMapper mapper, PostInteractionMapper interactions, ContentAgreementService agreement,
            ContentGovernanceService governance, RedisLockService locks, ContentMessageService messages,
            UserActionEventService events) {
        this.posts = posts;
        this.revisions = revisions;
        this.bookings = bookings;
        this.mapper = mapper;
        this.interactions = interactions;
        this.agreement = agreement;
        this.governance = governance;
        this.locks = locks;
        this.messages = messages;
        this.events = events;
    }

    @Override
    public PostRecruitmentVO create(long userId, long postId, PostRecruitmentRequest request) {
        governance.requireAllowed(userId, "RECRUITMENT");
        validate(request);
        return locks.execute(List.of(RedisLockType.RECRUITMENT.key(postId)), () -> {
            agreement.requireAccepted(userId);
            Post post = posts.selectForUpdate(postId);
            requireOwner(post, userId);
            if (!"PUBLISHED".equals(post.contentStatus)) throw conflict();
            PostRevision revision = revisions.selectForPost(post.currentRevisionId, postId);
            Booking booking = bookings.selectById(request.getBookingId());
            // 只接受动态已经绑定的同一预约，运动和场馆关系不能由招募请求另行替换。
            if (revision == null || !Objects.equals(revision.bookingId, request.getBookingId())
                    || booking == null || !Objects.equals(booking.userId, userId)
                    || booking.source != BookingSource.ONLINE
                    || booking.status != BookingStatus.CONFIRMED || !booking.startAt.isAfter(Instant.now())
                    || !Objects.equals(revision.venueId, booking.venueId)
                    || !Objects.equals(revision.sportTypeId, booking.sportTypeId)) throw invalid();
            if (mapper.selectByPostForUpdate(postId) != null) throw conflict();
            PostRecruitment row = new PostRecruitment();
            row.postId = postId;
            row.bookingId = booking.id;
            row.level = request.getLevel().trim();
            if (!LEVEL_TAGS.contains(row.level)) throw invalid();
            row.levelTag = row.level;
            row.maxMembers = request.getMaxMembers();
            row.feeMode = request.getFeeMode();
            row.aaAmountFen = request.getAaAmountFen();
            row.description = request.getDescription() == null ? null : request.getDescription().trim();
            row.status = "RECRUITING";
            try { if (mapper.insert(row) != 1) throw conflict(); }
            catch (DuplicateKeyException duplicate) { throw conflict(); }
            interactions.setCommentsEnabled(postId, true);
            return toVO(userId, post, row, booking);
        });
    }

    @Override
    public PostRecruitmentVO get(long userId, long postId) {
        governance.requireEnabled();
        if (posts.selectPublicById(userId, postId) == null) throw notFound();
        PostRecruitment row = mapper.selectByPost(postId);
        if (row == null) return null;
        return locks.execute(List.of(RedisLockType.RECRUITMENT.key(postId)), () -> {
            Post post = posts.selectForUpdate(postId);
            PostRecruitment current = mapper.selectByPostForUpdate(postId);
            Booking booking = bookings.selectById(current.bookingId);
            synchronize(post, current, booking);
            return toVO(userId, post, current, booking);
        });
    }

    @Override
    public void apply(long userId, long recruitmentId) {
        governance.requireAllowed(userId, "RECRUITMENT");
        mutate(recruitmentId, row -> {
            agreement.requireAccepted(userId);
            Post post = posts.selectForUpdate(row.postId);
            if (posts.selectPublicById(userId, row.postId) == null || post.authorId == userId
                    || !active(row) || blocked(userId, post.authorId)) throw conflict();
            PostRecruitmentMember existing = mapper.selectMember(row.id, userId);
            if (existing != null && List.of("APPLIED", "APPROVED").contains(existing.status)) return;
            if (mapper.countApproved(row.id) >= row.maxMembers) throw conflict();
            mapper.upsertApplication(row.id, userId);
            events.fact(userId,"RECRUITMENT_APPLY","RECRUITMENT",row.id);
            messages.record(post.authorId, "POST", post.id,
                    "application:" + row.id + ":" + userId + ":" + Instant.now().toEpochMilli(),
                    "新的找搭子申请", "有人申请加入你的招募");
        });
    }

    @Override
    public void decide(long userId, long recruitmentId, long applicantId, boolean approve) {
        governance.requireEnabled();
        mutate(recruitmentId, row -> {
            Post post = posts.selectForUpdate(row.postId);
            requireOwner(post, userId);
            if (!active(row) || blocked(userId, applicantId)) throw conflict();
            PostRecruitmentMember member = mapper.selectMember(row.id, applicantId);
            if (member == null) throw notFound();
            String result = approve ? "APPROVED" : "REJECTED";
            if (result.equals(member.status)) return;
            if (!"APPLIED".equals(member.status) || approve && mapper.countApproved(row.id) >= row.maxMembers) {
                throw conflict();
            }
            if (mapper.transitionMember(row.id, applicantId, "APPLIED", result, null) != 1) throw conflict();
            refreshCapacity(row);
            if (approve) events.fact(applicantId, "RECRUITMENT_APPROVED", "RECRUITMENT", row.id);
            messages.record(applicantId, "POST", row.postId,
                    "decision:" + row.id + ":" + applicantId + ":" + member.appliedAt + ":" + result,
                    approve ? "找搭子申请已通过" : "找搭子申请未通过", "你的申请已处理");
        });
    }

    @Override
    public void leave(long userId, long recruitmentId) {
        governance.requireEnabled();
        mutate(recruitmentId, row -> {
            PostRecruitmentMember member = mapper.selectMember(row.id, userId);
            if (member == null) throw notFound();
            if ("LEFT".equals(member.status)) return;
            if (!List.of("APPLIED", "APPROVED").contains(member.status)) throw conflict();
            if (mapper.transitionMember(row.id, userId, member.status, "LEFT", null) != 1) throw conflict();
            refreshCapacity(row);
        });
    }

    @Override
    public void remove(long userId, long recruitmentId, long memberId, String reason) {
        governance.requireEnabled();
        if (reason == null || reason.isBlank() || reason.trim().length() > 255) throw invalid();
        mutate(recruitmentId, row -> {
            requireOwner(posts.selectForUpdate(row.postId), userId);
            PostRecruitmentMember member = mapper.selectMember(row.id, memberId);
            if (member == null || !"APPROVED".equals(member.status)) throw conflict();
            if (mapper.transitionMember(row.id, memberId, "APPROVED", "REMOVED", reason.trim()) != 1) throw conflict();
            refreshCapacity(row);
            messages.record(memberId, "POST", row.postId, "removed:" + row.id + ":" + memberId + ":" + member.appliedAt,
                    "已退出找搭子", reason.trim());
        });
    }

    @Override
    public void close(long userId, long recruitmentId) {
        governance.requireEnabled();
        mutate(recruitmentId, row -> {
            requireOwner(posts.selectForUpdate(row.postId), userId);
            if (active(row)) closeAndNotify(row, "CLOSED");
        });
    }

    @Override
    public List<RecruitmentMemberVO> members(long userId, long recruitmentId) {
        governance.requireEnabled();
        PostRecruitment row = mapper.selectById(recruitmentId);
        if (row == null) throw notFound();
        PostRecruitmentVO view = get(userId, row.postId);
        if (!Boolean.TRUE.equals(view.getIsOwner()) && !"APPROVED".equals(view.getMyStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, ErrorCode.FORBIDDEN);
        }
        return view.getMembers();
    }

    /** 所有参与状态写入共用动态 ID 命名的招募锁和数据库事务。 */
    private void mutate(long id, Consumer<PostRecruitment> operation) {
        PostRecruitment initial = mapper.selectById(id);
        if (initial == null) throw notFound();
        locks.execute(List.of(RedisLockType.RECRUITMENT.key(initial.postId)), () -> {
            Post post = posts.selectForUpdate(initial.postId);
            PostRecruitment current = mapper.selectByIdForUpdate(id);
            Booking booking = bookings.selectById(current.bookingId);
            synchronize(post, current, booking);
            operation.accept(current);
            return null;
        });
    }

    @Scheduled(fixedDelay = 60000)
    public void closeExpiredRecruitments() {
        for (PostRecruitment row : mapper.selectExpired(Instant.now())) {
            mutate(row.id, current -> { });
        }
    }

    @Override
    public void closeForUser(long userId) {
        List<PostRecruitment> affected = mapper.selectAffectedByUser(userId);
        List<String> keys = affected.stream().map(row -> RedisLockType.RECRUITMENT.key(row.postId)).toList();
        locks.execute(keys, () -> {
            for (PostRecruitment candidate : affected) {
                Post post = posts.selectForUpdate(candidate.postId);
                PostRecruitment row = mapper.selectByIdForUpdate(candidate.id);
                if ("USER".equals(post.authorType) && post.authorId == userId) {
                    closeAndNotify(row,"CLOSED");
                } else {
                    PostRecruitmentMember member = mapper.selectMember(row.id,userId);
                    if (member != null && List.of("APPLIED","APPROVED").contains(member.status)) {
                        mapper.transitionMember(row.id,userId,member.status,"LEFT","账号已注销");
                        refreshCapacity(row);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public void closeForPost(long postId) {
        locks.execute(List.of(RedisLockType.RECRUITMENT.key(postId)), () -> {
            PostRecruitment row = mapper.selectByPostForUpdate(postId);
            if (row != null && active(row)) closeAndNotify(row,"CLOSED");
            return null;
        });
    }

    /** 取消、开始、删除和注销只关闭招募，绝不反向修改预约。 */
    private void synchronize(Post post, PostRecruitment row, Booking booking) {
        if (!active(row)) return;
        if (post == null || post.deletedAt != null || !"PUBLISHED".equals(post.contentStatus)) {
            closeAndNotify(row, "CLOSED");
        }
        else if (booking == null || booking.status != BookingStatus.CONFIRMED) {
            closeAndNotify(row, booking != null && booking.status == BookingStatus.CANCELLED
                    ? "BOOKING_CANCELLED" : "ENDED");
        } else if (!booking.startAt.isAfter(Instant.now())) closeAndNotify(row, "ENDED");
        else refreshCapacity(row);
    }

    private void closeAndNotify(PostRecruitment row, String status) {
        mapper.updateStatus(row.id, status);
        row.status = status;
        for (PostRecruitmentMember member : mapper.selectMembers(row.id)) {
            if (List.of("APPLIED", "APPROVED").contains(member.status)) {
                messages.record(member.userId, "POST", row.postId, "closed:" + row.id + ":" + member.userId,
                        "找搭子招募已结束", status.equals("BOOKING_CANCELLED") ? "关联预约已取消" : "招募已结束");
            }
        }
    }

    private void refreshCapacity(PostRecruitment row) {
        if (!active(row)) return;
        String status = mapper.countApproved(row.id) >= row.maxMembers ? "FULL" : "RECRUITING";
        if (!status.equals(row.status)) {
            mapper.updateStatus(row.id, status);
            row.status = status;
            if ("FULL".equals(status)) {
                Post post = posts.selectForUpdate(row.postId);
                events.fact(post.authorId,"RECRUITMENT_FILLED","POST",row.postId);
            }
        }
    }

    private PostRecruitmentVO toVO(long userId, Post post, PostRecruitment row, Booking booking) {
        boolean owner = post != null && "USER".equals(post.authorType) && post.authorId == userId;
        PostRecruitmentMember mine = mapper.selectMember(row.id, userId);
        boolean approved = mine != null && "APPROVED".equals(mine.status);
        // 失效或已隐藏的分享页只保留状态，不把历史成员关系带出公开详情。
        List<RecruitmentMemberVO> members = active(row) && (owner || approved)
                ? mapper.selectMembers(row.id).stream().map(RecruitmentMemberVO::from).toList() : List.of();
        return new PostRecruitmentVO(row.id, row.postId, row.level, row.levelTag, row.maxMembers, mapper.countApproved(row.id),
                row.feeMode, row.aaAmountFen, row.description, row.status,
                booking == null ? null : booking.venueName, booking == null ? null : booking.sportName,
                booking == null ? null : booking.startAt, booking == null ? null : booking.endAt,
                booking != null && (owner || approved) ? booking.courtName : null,
                owner, mine == null ? null : mine.status, members);
    }

    private boolean blocked(long userId, long authorId) {
        return Integer.valueOf(1).equals(interactions.isBlocked(userId, authorId));
    }

    private boolean active(PostRecruitment row) {
        return List.of("RECRUITING", "FULL").contains(row.status);
    }

    private void requireOwner(Post post, long userId) {
        if (post == null || !"USER".equals(post.authorType) || post.authorId != userId || post.deletedAt != null) {
            throw notFound();
        }
    }

    private void validate(PostRecruitmentRequest request) {
        if (request == null || request.getBookingId() == null || request.getBookingId() <= 0
                || request.getLevel() == null || !List.of("BEGINNER_FRIENDLY", "CASUAL", "ADVANCED").contains(request.getLevel().trim())
                || request.getMaxMembers() == null || request.getMaxMembers() < 1 || request.getMaxMembers() > 20
                || request.getFeeMode() == null || !List.of("FREE", "AA").contains(request.getFeeMode())
                || request.getAaAmountFen() == null || request.getAaAmountFen() < 0
                || "FREE".equals(request.getFeeMode()) && request.getAaAmountFen() != 0
                || request.getDescription() != null && request.getDescription().trim().length() > 500) throw invalid();
    }

    private ApiException invalid() { return new ApiException(HttpStatus.BAD_REQUEST, ErrorCode.INVALID_ARGUMENT); }
    private ApiException conflict() { return new ApiException(HttpStatus.CONFLICT, ErrorCode.OPERATION_IN_PROGRESS); }
    private ApiException notFound() { return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND); }
}

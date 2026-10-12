package com.sportsbooking.admin.server.config;

import com.sportsbooking.admin.biz.service.ContentMessageService;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** 只在明确演示环境生成固定社区样本，稳定 ID 保证重启不会覆盖人工修改。 */
@Component
@Profile("demo & !runtime-test")
@Order(4)
public class P4DemoDataRunner implements CommandLineRunner {
    private final JdbcTemplate jdbc;
    private final ContentMessageService messages;
    private final boolean enabled;

    public P4DemoDataRunner(JdbcTemplate jdbc, ContentMessageService messages,
                            @Value("${app.demo.seed-enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.messages = messages;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (!enabled) return;
        jdbc.update("""
            INSERT IGNORE INTO s_user_account (mock_openid,nickname,status)
            VALUES ('mock:local-demo-user','小林','ACTIVE'),('mock:p4-demo-partner','阿远','ACTIVE')
            """);
        long author = jdbc.queryForObject("SELECT id FROM s_user_account WHERE mock_openid='mock:local-demo-user'",Long.class);
        long partner = jdbc.queryForObject("SELECT id FROM s_user_account WHERE mock_openid='mock:p4-demo-partner'",Long.class);
        long admin = jdbc.queryForObject("SELECT MIN(id) FROM s_admin_account WHERE role='PLATFORM_ADMIN'",Long.class);
        jdbc.update("""
            INSERT IGNORE INTO s_content_agreement_acceptance (user_id,rules_version,privacy_version)
            SELECT ?,rules_version,privacy_version FROM s_content_agreement_setting WHERE id=1
            """,author);
        List<Map<String,Object>> venues = jdbc.queryForList("""
            SELECT v.id AS venueId,v.name AS venueName,MIN(c.id) AS courtId
            FROM s_venue v JOIN s_court c ON c.venue_id=v.id
            GROUP BY v.id,v.name ORDER BY v.id LIMIT 3
            """);
        List<Map<String,Object>> courts = jdbc.queryForList("""
            SELECT c.id,c.name,MIN(cs.sport_type_id) AS sportId
            FROM s_court c JOIN s_court_sport cs ON cs.court_id=c.id GROUP BY c.id,c.name
            """);
        java.util.Map<Long,Map<String,Object>> byCourt = new java.util.HashMap<>();
        for (Map<String,Object> court : courts) byCourt.put(((Number)court.get("id")).longValue(),court);
        List<Map<String,Object>> sports = jdbc.queryForList("SELECT id,name FROM s_sport_type WHERE owner_venue_id IS NULL");
        java.util.Map<Long,String> sportNames = new java.util.HashMap<>();
        for (Map<String,Object> sport : sports) sportNames.put(((Number)sport.get("id")).longValue(),(String)sport.get("name"));
        int index = 0;
        for (Map<String,Object> venue : venues) {
            index++;
            long venueId = ((Number)venue.get("venueId")).longValue();
            long courtId = ((Number)venue.get("courtId")).longValue();
            Map<String,Object> court = byCourt.get(courtId);
            long sportId = ((Number)court.get("sportId")).longValue();
            String sportName = sportNames.get(sportId);
            long completedBooking = 920000 + index;
            long futureBooking = 920100 + index;
            seedBooking(completedBooking,author,venueId,courtId,sportId,(String)venue.get("venueName"),
                    (String)court.get("name"),sportName,false,index);
            seedBooking(futureBooking,author,venueId,courtId,sportId,(String)venue.get("venueName"),
                    (String)court.get("name"),sportName,true,index);
            seedPost(910000+index,author,"USER",sportId,venueId,futureBooking,
                    sportName+"下班后一起运动，欢迎同水平的球友",true,false);
            seedPost(910100+index,venueId,"VENUE",sportId,venueId,null,
                    "本周场次已更新，欢迎来场馆运动",true,false);
            jdbc.update("""
                INSERT IGNORE INTO s_venue_review (id,booking_id,venue_id,user_id,sport_type_id)
                VALUES (?,?,?,?,?)
                """,930000+index,completedBooking,venueId,author,sportId);
            jdbc.update("""
                INSERT IGNORE INTO s_venue_review_revision
                (id,review_id,revision_no,body,overall_score,court_score,environment_score,service_score,anonymous,moderation_status)
                VALUES (?,?,1,'场地整洁，灯光明亮，这次运动很顺利',5,5,4,5,TRUE,'APPROVED')
                """,931000+index,930000+index);
            jdbc.update("""
                UPDATE s_venue_review SET current_revision_id=?,public_revision_id=?,content_status='PUBLISHED'
                WHERE id=? AND current_revision_id IS NULL
                """,931000+index,931000+index,930000+index);
            jdbc.update("""
                INSERT IGNORE INTO s_post_recruitment (id,post_id,booking_id,level,max_members,fee_mode,aa_amount_fen,description)
                VALUES (?,?,?,'基础',2,'FREE',0,'轻松运动，准时到场')
                """,960000+index,910000+index,futureBooking);
        }
        if (!venues.isEmpty()) {
            long venueId = ((Number)venues.get(0).get("venueId")).longValue();
            long sportId = ((Number)byCourt.get(((Number)venues.get(0).get("courtId")).longValue()).get("sportId")).longValue();
            seedPost(910200,admin,"PLATFORM",sportId,null,null,"约场社区公告：运动交流请尊重他人隐私",true,true);
            seedPost(910201,author,"USER",sportId,venueId,null,"运动交流讨论",false,false);
            seedComment(950001,910001,partner,null,"场馆环境很好，下次一起运动");
            seedComment(950002,910001,author,950001L,"欢迎，下次约同一个场次");
            jdbc.update("""
                INSERT IGNORE INTO s_content_moderation_log (id,content_type,content_id,revision_id,action,reason,operator_admin_id)
                VALUES (940001,'POST',910201,911201,'HIDE','等待内容复核',?)
                """,admin);
            jdbc.update("""
                INSERT IGNORE INTO s_content_report (id,reporter_user_id,target_type,target_id,reason_code,description)
                VALUES (940101,?,'POST',910201,'OTHER','希望平台核对内容')
                """,partner);
            jdbc.update("""
                INSERT IGNORE INTO s_content_appeal (id,moderation_log_id,appellant_user_id,body)
                VALUES (940201,940001,?,'申请重新核对这条运动动态')
                """,author);
            jdbc.update("UPDATE s_post SET legal_hold=TRUE WHERE id=910201");
            messages.record(author,"POST",910001,"demo-comment:950001","动态收到评论","有球友评论了你的动态");
        }
    }

    private void seedBooking(long id,long userId,long venueId,long courtId,long sportId,String venueName,
                             String courtName,String sportName,boolean future,int index) {
        Instant start = Instant.now().plusSeconds((future ? 3 : -2)*86400L+index*113L);
        Timestamp startAt = Timestamp.from(start);
        Timestamp endAt = Timestamp.from(start.plusSeconds(3600));
        jdbc.update("""
            INSERT IGNORE INTO s_session_slot (id,venue_id,court_id,sport_type_id,slot_date,start_at,end_at,price_fen,status)
            VALUES (?,?,?,?,DATE(?),?,?,6000,'BOOKED')
            """,id,venueId,courtId,sportId,startAt,startAt,endAt);
        jdbc.update("""
            INSERT IGNORE INTO s_booking (id,booking_no,user_id,venue_id,court_id,sport_type_id,session_slot_id,source,
                contact_name,contact_phone,venue_name,court_name,sport_name,start_at,end_at,amount_fen,external_amount_fen,
                service_fee_rate_basis_points,status,payment_status,completed_at)
            VALUES (?,?,?,?,?,?,?,'ONLINE','本地样本','00000000000',?,?,?,?,?,6000,6000,0,?,'PAID',?)
            """,id,"P4-SAMPLE-"+id,userId,venueId,courtId,sportId,id,venueName,courtName,sportName,startAt,endAt,
                future ? "CONFIRMED" : "COMPLETED",future ? null : endAt);
    }

    private void seedPost(long id,long authorId,String authorType,long sportId,Long venueId,Long bookingId,
                           String body,boolean published,boolean pinned) {
        jdbc.update("""
            INSERT IGNORE INTO s_post (id,author_type,author_id,is_official,is_pinned,pinned_slot)
            VALUES (?,?,?,?,?,?)
            """,id,authorType,authorId,!"USER".equals(authorType),pinned,pinned ? 1 : null);
        long revisionId = id+1000;
        jdbc.update("""
            INSERT IGNORE INTO s_post_revision (id,post_id,revision_no,body,sport_type_id,venue_id,booking_id,moderation_status)
            VALUES (?,?,1,?,?,?,?,'APPROVED')
            """,revisionId,id,body,sportId,venueId,bookingId);
        jdbc.update("""
            UPDATE s_post SET current_revision_id=?,public_revision_id=?,content_status=?
            WHERE id=? AND current_revision_id IS NULL
            """,revisionId,revisionId,published ? "PUBLISHED" : "HIDDEN",id);
    }

    private void seedComment(long id,long postId,long userId,Long parentId,String body) {
        jdbc.update("INSERT IGNORE INTO s_post_comment (id,post_id,author_user_id,parent_comment_id) VALUES (?,?,?,?)",id,postId,userId,parentId);
        jdbc.update("""
            INSERT IGNORE INTO s_post_comment_revision (id,comment_id,revision_no,body,moderation_status)
            VALUES (?,?,1,?,'APPROVED')
            """,id+1000,id,body);
        jdbc.update("""
            UPDATE s_post_comment SET current_revision_id=?,public_revision_id=?,content_status='PUBLISHED'
            WHERE id=? AND current_revision_id IS NULL
            """,id+1000,id+1000,id);
    }
}

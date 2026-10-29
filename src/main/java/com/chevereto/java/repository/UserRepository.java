package com.chevereto.java.repository;

import com.chevereto.java.entity.User;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

/** chv_users —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface UserRepository {

    String COLS = "user_id, user_username, user_name, user_email, user_date, user_date_gmt, "
            + "user_bio, user_website, user_timezone, user_language, user_status, user_is_admin, "
            + "user_is_private, user_show_nsfw_listings, user_image_count, user_album_count, "
            + "user_registration_ip, user_avatar_filename";

    @Select("SELECT " + COLS + " FROM chv_users WHERE user_id = #{id}")
    Optional<User> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_users ORDER BY user_id ASC")
    List<User> findAll();

    @Select("SELECT COUNT(*) FROM chv_users")
    long count();

    default List<User> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIds(ids);
    }

    @Select("<script>SELECT " + COLS + " FROM chv_users WHERE user_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<User> findByIds(@Param("ids") List<Long> ids);

    @Select("SELECT " + COLS + " FROM chv_users WHERE user_username = #{username} LIMIT 1")
    Optional<User> findByUserUsername(String username);

    @Select("SELECT " + COLS + " FROM chv_users WHERE user_email = #{email} LIMIT 1")
    Optional<User> findByUserEmail(String email);

    @Select("SELECT COUNT(*) FROM chv_users WHERE user_username = #{username}")
    boolean existsByUserUsername(String username);

    @Select("SELECT COUNT(*) FROM chv_users WHERE user_email = #{email}")
    boolean existsByUserEmail(String email);

    @Insert("INSERT INTO chv_users "
            + "(user_username, user_name, user_email, user_date, user_date_gmt, user_bio, user_website, "
            + "user_timezone, user_language, user_status, user_is_admin, user_is_private, "
            + "user_show_nsfw_listings, user_image_count, user_album_count, user_registration_ip, "
            + "user_avatar_filename, user_is_manager, user_palette_id, user_newsletter_subscribe, "
            + "user_image_keep_exif, user_likes, user_liked, user_following, user_followers, "
            + "user_content_views, user_notifications_unread) "
            + "VALUES "
            + "(#{userUsername}, #{userName}, #{userEmail}, #{userDate}, #{userDateGmt}, #{userBio}, #{userWebsite}, "
            + "#{userTimezone}, #{userLanguage}, #{userStatus}, #{userIsAdmin}, #{userIsPrivate}, "
            + "#{userShowNsfwListings}, #{userImageCount}, #{userAlbumCount}, #{userRegistrationIp}, "
            + "#{userAvatarFilename}, 0, 0, 1, 1, 0, 0, 0, 0, 0, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "userId")
    int insert(User user);

    @Update("UPDATE chv_users SET "
            + "user_username = #{userUsername}, user_name = #{userName}, user_email = #{userEmail}, "
            + "user_bio = #{userBio}, user_website = #{userWebsite}, user_timezone = #{userTimezone}, "
            + "user_language = #{userLanguage}, user_status = #{userStatus}, user_is_admin = #{userIsAdmin}, "
            + "user_is_private = #{userIsPrivate}, user_show_nsfw_listings = #{userShowNsfwListings}, "
            + "user_image_count = #{userImageCount}, user_album_count = #{userAlbumCount}, "
            + "user_avatar_filename = #{userAvatarFilename} "
            + "WHERE user_id = #{userId}")
    int update(User user);

    default User save(User user) {
        if (user.getUserId() == null) {
            insert(user);
        } else {
            update(user);
        }
        return user;
    }

    @Delete("DELETE FROM chv_users WHERE user_id = #{id}")
    int deleteById(Long id);

    default void delete(User user) {
        deleteById(user.getUserId());
    }

    @Select("SELECT " + COLS + " FROM chv_users ORDER BY user_id ASC LIMIT #{limit} OFFSET #{offset}")
    List<User> findPageList(@Param("offset") int offset, @Param("limit") int limit);

    default Page<User> findAll(Pageable pageable) {
        return new PageImpl<>(findPageList((int) pageable.getOffset(), pageable.getPageSize()),
                pageable, count());
    }
}

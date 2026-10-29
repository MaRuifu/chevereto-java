package com.chevereto.java.repository;

import com.chevereto.java.entity.Album;
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

/** chv_albums —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface AlbumRepository {

    String COLS = "album_id, album_name, album_user_id, album_date, album_date_gmt, album_creation_ip, "
            + "album_privacy, album_description, album_password, album_image_count, album_views, "
            + "album_likes, album_cover_id, album_parent_id";

    @Select("SELECT " + COLS + " FROM chv_albums WHERE album_id = #{id}")
    Optional<Album> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_albums ORDER BY album_id DESC")
    List<Album> findAll();

    @Select("SELECT COUNT(*) FROM chv_albums")
    long count();

    default List<Album> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIds(ids);
    }

    @Select("<script>SELECT " + COLS + " FROM chv_albums WHERE album_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Album> findByIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO chv_albums "
            + "(album_name, album_user_id, album_date, album_date_gmt, album_creation_ip, "
            + "album_privacy, album_description, album_password, album_image_count, album_views, "
            + "album_likes, album_cover_id, album_parent_id) "
            + "VALUES "
            + "(#{albumName}, #{albumUserId}, #{albumDate}, #{albumDateGmt}, #{albumCreationIp}, "
            + "#{albumPrivacy}, #{albumDescription}, #{albumPassword}, #{albumImageCount}, #{albumViews}, "
            + "#{albumLikes}, #{albumCoverId}, #{albumParentId})")
    @Options(useGeneratedKeys = true, keyProperty = "albumId")
    int insert(Album album);

    @Update("UPDATE chv_albums SET "
            + "album_name = #{albumName}, album_user_id = #{albumUserId}, album_privacy = #{albumPrivacy}, "
            + "album_description = #{albumDescription}, album_password = #{albumPassword}, "
            + "album_image_count = #{albumImageCount}, album_views = #{albumViews}, album_likes = #{albumLikes}, "
            + "album_cover_id = #{albumCoverId}, album_parent_id = #{albumParentId} "
            + "WHERE album_id = #{albumId}")
    int update(Album album);

    default Album save(Album album) {
        if (album.getAlbumId() == null) {
            insert(album);
        } else {
            update(album);
        }
        return album;
    }

    @Delete("DELETE FROM chv_albums WHERE album_id = #{id}")
    int deleteById(Long id);

    default void delete(Album album) {
        deleteById(album.getAlbumId());
    }

    // ---- 分页查询 ----

    @Select("SELECT " + COLS + " FROM chv_albums WHERE album_user_id = #{userId}"
            + " ORDER BY album_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Album> findByUserList(@Param("userId") Long userId, @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM chv_albums WHERE album_user_id = #{userId}")
    long countByUser(Long userId);

    default Page<Album> findByAlbumUserIdOrderByAlbumIdDesc(Long userId, Pageable pageable) {
        return new PageImpl<>(findByUserList(userId, (int) pageable.getOffset(), pageable.getPageSize()),
                pageable, countByUser(userId));
    }

    @Select("SELECT " + COLS + " FROM chv_albums WHERE album_user_id = #{userId} AND album_privacy <> #{privacy}"
            + " ORDER BY album_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Album> findByUserNotPrivacyList(@Param("userId") Long userId, @Param("privacy") String privacy,
                                         @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM chv_albums WHERE album_user_id = #{userId} AND album_privacy <> #{privacy}")
    long countByUserNotPrivacy(@Param("userId") Long userId, @Param("privacy") String privacy);

    default Page<Album> findByAlbumUserIdAndAlbumPrivacyNotOrderByAlbumIdDesc(Long userId, String privacy,
                                                                              Pageable pageable) {
        return new PageImpl<>(
                findByUserNotPrivacyList(userId, privacy, (int) pageable.getOffset(), pageable.getPageSize()),
                pageable, countByUserNotPrivacy(userId, privacy));
    }

    @Select("SELECT " + COLS + " FROM chv_albums WHERE album_privacy = #{privacy}"
            + " ORDER BY album_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Album> findByPrivacyList(@Param("privacy") String privacy,
                                  @Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM chv_albums WHERE album_privacy = #{privacy}")
    long countByPrivacy(String privacy);

    default Page<Album> findByAlbumPrivacyOrderByAlbumIdDesc(String privacy, Pageable pageable) {
        return new PageImpl<>(findByPrivacyList(privacy, (int) pageable.getOffset(), pageable.getPageSize()),
                pageable, countByPrivacy(privacy));
    }
}

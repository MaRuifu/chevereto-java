package com.chevereto.java.repository;

import com.chevereto.java.entity.Image;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * chv_images —— MyBatis 注解 SQL（无 XML）。
 * 列名与实体驼峰字段一一对应（开启 map-underscore-to-camel-case 自动映射）。
 */
@Mapper
public interface ImageRepository {

    String COLS = "image_id, image_name, image_extension, image_size, image_width, image_height, "
            + "image_date, image_date_gmt, image_title, image_description, image_nsfw, "
            + "image_user_id, image_album_id, image_uploader_ip, image_storage_mode, image_path, "
            + "image_storage_id, image_md5, image_source_md5, image_original_filename, image_views, "
            + "image_category_id, image_thumb_size, image_medium_size, image_likes, "
            + "image_is_animated, image_is_approved";

    /**
     * 原版 V4 无 image_privacy 列（图片隐私由专辑 chv_albums.album_privacy 控制），
     * 公开列表不再按图片级隐私过滤，恒为真条件保留查询结构。
     */
    String PUBLIC_WHERE = "(1=1)";

    /**
     * NSFW 过滤片段（对齐原版 Listing 类的 privacy layer）：
     * <pre>
     *   allowNsfw=false 且 游客     -> image_nsfw = 0
     *   allowNsfw=false 且 已登录   -> (image_nsfw = 0 OR (image_nsfw = 1 AND image_user_id = 我))
     *   allowNsfw=true              -> 不加条件
     * </pre>
     * 「allowNsfw 怎么算」由 NsfwPolicy 决定（管理员恒真、看自己主页恒真、其余看用户偏好或全局开关）。
     */
    String NSFW_CLAUSE = "<if test='!allowNsfw'> AND (image_nsfw = 0"
            + "<if test='viewerId != null'> OR (image_nsfw = 1 AND image_user_id = #{viewerId})</if>)</if>";


    @Select("SELECT " + COLS + " FROM chv_images WHERE image_id = #{id}")
    Optional<Image> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_images ORDER BY image_id DESC")
    List<Image> findAll();

    @Select("SELECT COUNT(*) FROM chv_images")
    long count();

    @Select("SELECT " + COLS + " FROM chv_images ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}")
    List<Image> findPageList(@Param("offset") int offset, @Param("limit") int limit);

    default Page<Image> findAll(Pageable pageable) {
        return new PageImpl<>(findPageList((int) pageable.getOffset(), pageable.getPageSize()),
                pageable, count());
    }

    default List<Image> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIds(ids);
    }

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE image_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Image> findByIds(@Param("ids") List<Long> ids);

    // ---- 插入 / 更新 ----

    @Insert("INSERT INTO chv_images "
            + "(image_name, image_extension, image_size, image_width, image_height, "
            + "image_date, image_date_gmt, image_title, image_description, image_nsfw, "
            + "image_user_id, image_album_id, image_uploader_ip, image_storage_mode, image_path, "
            + "image_storage_id, image_md5, image_source_md5, image_original_filename, image_views, "
            + "image_category_id, image_thumb_size, image_medium_size, image_likes, "
            + "image_is_animated, image_is_approved, image_chain, image_is_360) "
            + "VALUES "
            + "(#{imageName}, #{imageExtension}, #{imageSize}, #{imageWidth}, #{imageHeight}, "
            + "#{imageDate}, #{imageDateGmt}, #{imageTitle}, #{imageDescription}, #{imageNsfw}, "
            + "#{imageUserId}, #{imageAlbumId}, #{imageUploaderIp}, #{imageStorageMode}, #{imagePath}, "
            + "#{imageStorageId}, #{imageMd5}, #{imageSourceMd5}, #{imageOriginalFilename}, #{imageViews}, "
            + "#{imageCategoryId}, #{imageThumbSize}, #{imageMediumSize}, #{imageLikes}, "
            + "#{imageIsAnimated}, #{imageIsApproved}, 0, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "imageId")
    int insert(Image image);

    @Update("UPDATE chv_images SET "
            + "image_name = #{imageName}, image_extension = #{imageExtension}, image_size = #{imageSize}, "
            + "image_width = #{imageWidth}, image_height = #{imageHeight}, image_title = #{imageTitle}, "
            + "image_description = #{imageDescription}, image_nsfw = #{imageNsfw}, image_user_id = #{imageUserId}, "
            + "image_album_id = #{imageAlbumId}, image_uploader_ip = #{imageUploaderIp}, "
            + "image_storage_mode = #{imageStorageMode}, image_path = #{imagePath}, "
            + "image_storage_id = #{imageStorageId}, image_md5 = #{imageMd5}, image_source_md5 = #{imageSourceMd5}, "
            + "image_original_filename = #{imageOriginalFilename}, image_views = #{imageViews}, "
            + "image_category_id = #{imageCategoryId}, image_thumb_size = #{imageThumbSize}, "
            + "image_medium_size = #{imageMediumSize}, image_likes = #{imageLikes}, "
            + "image_is_animated = #{imageIsAnimated}, image_is_approved = #{imageIsApproved} "
            + "WHERE image_id = #{imageId}")
    int update(Image image);

    default Image save(Image image) {
        if (image.getImageId() == null) {
            insert(image);
        } else {
            update(image);
        }
        return image;
    }

    @Delete("DELETE FROM chv_images WHERE image_id = #{id}")
    int deleteById(Long id);

    default void delete(Image image) {
        deleteById(image.getImageId());
    }

    // ---- 业务查询 ----

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE " + PUBLIC_WHERE
            + NSFW_CLAUSE
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> findPublicList(@Param("offset") int offset, @Param("limit") int limit,
                               @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE " + PUBLIC_WHERE + NSFW_CLAUSE + "</script>")
    long countPublic(@Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    default Page<Image> findPublic(Pageable pageable, boolean allowNsfw, Long viewerId) {
        return new PageImpl<>(
                findPublicList((int) pageable.getOffset(), pageable.getPageSize(), allowNsfw, viewerId),
                pageable, countPublic(allowNsfw, viewerId));
    }

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE " + PUBLIC_WHERE
            + " AND image_category_id = #{categoryId}" + NSFW_CLAUSE
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> findPublicByCategoryList(@Param("categoryId") Long categoryId,
                                         @Param("offset") int offset, @Param("limit") int limit,
                                         @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE " + PUBLIC_WHERE
            + " AND image_category_id = #{categoryId}" + NSFW_CLAUSE + "</script>")
    long countPublicByCategory(@Param("categoryId") Long categoryId,
                               @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    default Page<Image> findPublicByCategory(Long categoryId, Pageable pageable,
                                             boolean allowNsfw, Long viewerId) {
        return new PageImpl<>(
                findPublicByCategoryList(categoryId, (int) pageable.getOffset(), pageable.getPageSize(),
                        allowNsfw, viewerId),
                pageable, countPublicByCategory(categoryId, allowNsfw, viewerId));
    }

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE image_user_id = #{userId}"
            + NSFW_CLAUSE
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> findByUserList(@Param("userId") Long userId, @Param("offset") int offset,
                               @Param("limit") int limit,
                               @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE image_user_id = #{userId}" + NSFW_CLAUSE + "</script>")
    long countByImageUserId(@Param("userId") Long userId,
                            @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    default Page<Image> findByImageUserIdOrderByImageIdDesc(Long userId, Pageable pageable,
                                                            boolean allowNsfw, Long viewerId) {
        return new PageImpl<>(
                findByUserList(userId, (int) pageable.getOffset(), pageable.getPageSize(), allowNsfw, viewerId),
                pageable, countByImageUserId(userId, allowNsfw, viewerId));
    }

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE image_album_id = #{albumId}"
            + NSFW_CLAUSE
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> findByAlbumList(@Param("albumId") Long albumId, @Param("offset") int offset,
                                @Param("limit") int limit,
                                @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE image_album_id = #{albumId}" + NSFW_CLAUSE + "</script>")
    long countByImageAlbumId(@Param("albumId") Long albumId,
                             @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    default Page<Image> findByImageAlbumIdOrderByImageIdDesc(Long albumId, Pageable pageable,
                                                             boolean allowNsfw, Long viewerId) {
        return new PageImpl<>(
                findByAlbumList(albumId, (int) pageable.getOffset(), pageable.getPageSize(), allowNsfw, viewerId),
                pageable, countByImageAlbumId(albumId, allowNsfw, viewerId));
    }

    @Select("SELECT " + COLS + " FROM chv_images WHERE image_album_id = #{albumId} ORDER BY image_id ASC LIMIT 1")
    Optional<Image> findFirstByImageAlbumIdOrderByImageIdAsc(Long albumId);

    @Select("SELECT " + COLS + " FROM chv_images WHERE image_md5 = #{md5} ORDER BY image_id ASC LIMIT 1")
    Optional<Image> findFirstByImageMd5OrderByImageIdAsc(String md5);

    /**
     * 原版 Image::isDuplicatedUpload()（app/src/Legacy/Classes/Image.php:557）：
     * <pre>
     * SELECT * FROM images WHERE (image_md5=:md5 OR image_source_md5=:md5)
     *   AND image_uploader_ip=:ip AND image_date_gmt &gt; :date_gmt
     * </pre>
     * 判定「同一上传者 IP 在最近 24 小时内已经传过同样的内容」。
     * image_md5 是打水印后的文件 md5，image_source_md5 是原始文件 md5，两者任一命中即算重复。
     */
    @Select("SELECT COUNT(*) FROM chv_images "
            + "WHERE (image_md5 = #{md5} OR image_source_md5 = #{md5}) "
            + "AND image_uploader_ip = #{ip} AND image_date_gmt > #{since}")
    long countDuplicatedUpload(@Param("md5") String md5, @Param("ip") String ip,
                               @Param("since") LocalDateTime since);

    // ---- 存储用量统计（管理后台「外部存储」页） ----

    @Select("SELECT COUNT(*) FROM chv_images WHERE image_storage_id = #{id}")
    long countByStorageId(Long id);

    @Select("SELECT COALESCE(SUM(image_size), 0) FROM chv_images WHERE image_storage_id = #{id}")
    long sumSizeByStorageId(Long id);

    /** 本地隐式存储（image_storage_id 为 NULL 的迁移老图）数量与容量 */
    @Select("SELECT COUNT(*) FROM chv_images WHERE image_storage_id IS NULL")
    long countByStorageIdNull();

    @Select("SELECT COALESCE(SUM(image_size), 0) FROM chv_images WHERE image_storage_id IS NULL")
    long sumSizeByStorageIdNull();

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE image_storage_id = #{id} "
            + "<if test='includeNull'>OR image_storage_id IS NULL</if>"
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> findByStorageList(@Param("id") Long id, @Param("includeNull") boolean includeNull,
                                  @Param("offset") int offset, @Param("limit") int limit);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE image_storage_id = #{id} "
            + "<if test='includeNull'>OR image_storage_id IS NULL</if></script>")
    long countByStorage(@Param("id") Long id, @Param("includeNull") boolean includeNull);

    default Page<Image> findByStorage(Long id, boolean includeNull, Pageable pageable) {
        return new PageImpl<>(findByStorageList(id, includeNull, (int) pageable.getOffset(), pageable.getPageSize()),
                pageable, countByStorage(id, includeNull));
    }

    @Select("SELECT " + COLS + " FROM chv_images WHERE image_path = #{imagePath} LIMIT 1")
    Optional<Image> findByImagePath(String imagePath);

    @Select("SELECT " + COLS + " FROM chv_images WHERE image_path LIKE CONCAT(#{pathPrefix}, '%')"
            + " ORDER BY image_id ASC LIMIT 1")
    Optional<Image> findFirstByImagePathStartingWithOrderByImageIdAsc(String pathPrefix);

    String SEARCH_WHERE = "(LOWER(image_title) LIKE CONCAT('%', #{q}, '%')"
            + " OR LOWER(image_name) LIKE CONCAT('%', #{q}, '%')"
            + " OR LOWER(image_original_filename) LIKE CONCAT('%', #{q}, '%'))";

    @Select("<script>SELECT " + COLS + " FROM chv_images WHERE " + PUBLIC_WHERE + " AND " + SEARCH_WHERE
            + NSFW_CLAUSE
            + " ORDER BY image_id DESC LIMIT #{limit} OFFSET #{offset}</script>")
    List<Image> searchList(@Param("q") String q, @Param("offset") int offset, @Param("limit") int limit,
                          @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    @Select("<script>SELECT COUNT(*) FROM chv_images WHERE " + PUBLIC_WHERE + " AND " + SEARCH_WHERE
            + NSFW_CLAUSE + "</script>")
    long countSearch(@Param("q") String q,
                     @Param("allowNsfw") boolean allowNsfw, @Param("viewerId") Long viewerId);

    default Page<Image> search(String q, Pageable pageable, boolean allowNsfw, Long viewerId) {
        return new PageImpl<>(
                searchList(q, (int) pageable.getOffset(), pageable.getPageSize(), allowNsfw, viewerId),
                pageable, countSearch(q, allowNsfw, viewerId));
    }
}

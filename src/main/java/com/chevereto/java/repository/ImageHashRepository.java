package com.chevereto.java.repository;

import com.chevereto.java.entity.ImageHash;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/** chv_images_hash —— 图片删图口令（对齐原版 Image::verifyPassword） */
@Mapper
public interface ImageHashRepository {

    String COLS = "image_hash_image_id, image_hash_hash";

    @Select("SELECT " + COLS + " FROM chv_images_hash WHERE image_hash_image_id = #{imageId}")
    Optional<ImageHash> findById(Long imageId);

    @Insert("INSERT INTO chv_images_hash (image_hash_image_id, image_hash_hash) "
            + "VALUES (#{imageHashImageId}, #{imageHashHash})")
    int insert(ImageHash imageHash);

    @Delete("DELETE FROM chv_images_hash WHERE image_hash_image_id = #{imageId}")
    int deleteByImageId(Long imageId);

    /** 原版是单条口令一对一（PK 即 image_id），重新签发时先删后插 */
    default void save(ImageHash imageHash) {
        deleteByImageId(imageHash.getImageHashImageId());
        insert(imageHash);
    }
}

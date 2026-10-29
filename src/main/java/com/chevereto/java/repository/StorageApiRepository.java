package com.chevereto.java.repository;

import com.chevereto.java.entity.StorageApi;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** chv_storage_apis —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface StorageApiRepository {

    String COLS = "storage_api_id, storage_api_name, storage_api_type";

    @Select("SELECT " + COLS + " FROM chv_storage_apis WHERE storage_api_id = #{id}")
    Optional<StorageApi> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_storage_apis ORDER BY storage_api_id ASC")
    List<StorageApi> findAll();

    @Select("SELECT COUNT(*) FROM chv_storage_apis")
    long count();

    /** 按驱动类型查（storage_service 为 NULL 的存储行按 api type 路由时使用） */
    @Select("SELECT " + COLS + " FROM chv_storage_apis WHERE storage_api_type = #{type} ORDER BY storage_api_id ASC LIMIT 1")
    Optional<StorageApi> findByType(String type);

    @Insert("INSERT INTO chv_storage_apis (storage_api_name, storage_api_type) "
            + "VALUES (#{storageApiName}, #{storageApiType})")
    @Options(useGeneratedKeys = true, keyProperty = "storageApiId")
    int insert(StorageApi storageApi);

    @Update("UPDATE chv_storage_apis SET "
            + "storage_api_name = #{storageApiName}, storage_api_type = #{storageApiType} "
            + "WHERE storage_api_id = #{storageApiId}")
    int update(StorageApi storageApi);

    default StorageApi save(StorageApi storageApi) {
        if (storageApi.getStorageApiId() == null) {
            insert(storageApi);
        } else {
            update(storageApi);
        }
        return storageApi;
    }
}

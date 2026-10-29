package com.chevereto.java.repository;

import com.chevereto.java.entity.Storage;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** chv_storages —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface StorageRepository {

    String COLS = "storage_id, storage_api_id, storage_name, storage_service, storage_url, storage_bucket, "
            + "storage_region, storage_server, storage_account_id, storage_account_name, storage_key, "
            + "storage_secret, storage_is_https, storage_is_active, storage_capacity, storage_space_used";

    @Select("SELECT " + COLS + " FROM chv_storages WHERE storage_id = #{id}")
    Optional<Storage> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_storages ORDER BY storage_id ASC")
    List<Storage> findAll();

    @Select("SELECT " + COLS + " FROM chv_storages ORDER BY storage_id ASC")
    List<Storage> findAllByOrderByStorageIdAsc();

    @Select("SELECT COUNT(*) FROM chv_storages")
    long count();

    default List<Storage> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIds(ids);
    }

    @Select("<script>SELECT " + COLS + " FROM chv_storages WHERE storage_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Storage> findByIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO chv_storages "
            + "(storage_api_id, storage_name, storage_service, storage_url, storage_bucket, storage_region, "
            + "storage_server, storage_account_id, storage_account_name, storage_key, storage_secret, "
            + "storage_is_https, storage_is_active, storage_capacity, storage_space_used) "
            + "VALUES "
            + "(#{storageApiId}, #{storageName}, #{storageService}, #{storageUrl}, #{storageBucket}, #{storageRegion}, "
            + "#{storageServer}, #{storageAccountId}, #{storageAccountName}, #{storageKey}, #{storageSecret}, "
            + "#{storageIsHttps}, #{storageIsActive}, #{storageCapacity}, #{storageSpaceUsed})")
    @Options(useGeneratedKeys = true, keyProperty = "storageId")
    int insert(Storage storage);

    @Update("UPDATE chv_storages SET "
            + "storage_api_id = #{storageApiId}, storage_name = #{storageName}, storage_service = #{storageService}, "
            + "storage_url = #{storageUrl}, storage_bucket = #{storageBucket}, storage_region = #{storageRegion}, "
            + "storage_server = #{storageServer}, storage_account_id = #{storageAccountId}, "
            + "storage_account_name = #{storageAccountName}, storage_key = #{storageKey}, "
            + "storage_secret = #{storageSecret}, storage_is_https = #{storageIsHttps}, "
            + "storage_is_active = #{storageIsActive}, storage_capacity = #{storageCapacity}, "
            + "storage_space_used = #{storageSpaceUsed} "
            + "WHERE storage_id = #{storageId}")
    int update(Storage storage);

    default Storage save(Storage storage) {
        if (storage.getStorageId() == null) {
            insert(storage);
        } else {
            update(storage);
        }
        return storage;
    }

    @Delete("DELETE FROM chv_storages WHERE storage_id = #{id}")
    int deleteById(Long id);

    default void delete(Storage storage) {
        deleteById(storage.getStorageId());
    }
}

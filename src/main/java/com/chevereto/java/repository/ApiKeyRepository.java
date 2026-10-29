package com.chevereto.java.repository;

import com.chevereto.java.entity.ApiKey;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/** chv_api_keys —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface ApiKeyRepository {

    String COLS = "api_key_id, api_key_user_id, api_key_name, api_key_date_gmt, api_key_hash";

    @Select("SELECT " + COLS + " FROM chv_api_keys WHERE api_key_id = #{id}")
    Optional<ApiKey> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_api_keys WHERE api_key_hash = #{hash} LIMIT 1")
    Optional<ApiKey> findByApiKeyHash(String hash);

    @Select("SELECT " + COLS + " FROM chv_api_keys WHERE api_key_user_id = #{userId} LIMIT 1")
    Optional<ApiKey> findFirstByApiKeyUserId(Long userId);

    @Insert("INSERT INTO chv_api_keys "
            + "(api_key_user_id, api_key_name, api_key_date_gmt, api_key_hash) "
            + "VALUES (#{apiKeyUserId}, #{apiKeyName}, #{apiKeyDateGmt}, #{apiKeyHash})")
    @Options(useGeneratedKeys = true, keyProperty = "apiKeyId")
    int insert(ApiKey apiKey);

    default ApiKey save(ApiKey apiKey) {
        if (apiKey.getApiKeyId() == null) {
            insert(apiKey);
        }
        return apiKey;
    }

    @Delete("DELETE FROM chv_api_keys WHERE api_key_user_id = #{userId}")
    int deleteByApiKeyUserId(Long userId);

    @org.apache.ibatis.annotations.Update("UPDATE chv_api_keys SET api_key_hash = #{hash} WHERE api_key_id = #{id}")
    int updateHash(@org.apache.ibatis.annotations.Param("id") Long id,
                   @org.apache.ibatis.annotations.Param("hash") String hash);
}

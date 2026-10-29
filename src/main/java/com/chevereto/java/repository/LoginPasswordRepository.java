package com.chevereto.java.repository;

import com.chevereto.java.entity.LoginPassword;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.Optional;

/** chv_login_passwords —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface LoginPasswordRepository {

    String COLS = "login_password_id, login_password_user_id, login_password_date_gmt, login_password_hash";

    @Select("SELECT " + COLS + " FROM chv_login_passwords WHERE login_password_id = #{id}")
    Optional<LoginPassword> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_login_passwords WHERE login_password_user_id = #{userId} LIMIT 1")
    Optional<LoginPassword> findByLoginPasswordUserId(Long userId);

    @Select("SELECT COUNT(*) FROM chv_login_passwords")
    long count();

    @Insert("INSERT INTO chv_login_passwords "
            + "(login_password_user_id, login_password_date_gmt, login_password_hash) "
            + "VALUES (#{loginPasswordUserId}, #{loginPasswordDateGmt}, #{loginPasswordHash})")
    @Options(useGeneratedKeys = true, keyProperty = "loginPasswordId")
    int insert(LoginPassword loginPassword);

    @Delete("DELETE FROM chv_login_passwords WHERE login_password_user_id = #{userId}")
    int deleteByLoginPasswordUserId(Long userId);

    default LoginPassword save(LoginPassword loginPassword) {
        if (loginPassword.getLoginPasswordId() == null) {
            insert(loginPassword);
        } else {
            deleteByLoginPasswordUserId(loginPassword.getLoginPasswordUserId());
            insert(loginPassword);
        }
        return loginPassword;
    }
}

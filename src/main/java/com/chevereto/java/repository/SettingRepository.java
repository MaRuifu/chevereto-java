package com.chevereto.java.repository;

import com.chevereto.java.entity.Setting;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** chv_settings —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface SettingRepository {

    String COLS = "setting_id, setting_name, setting_value, setting_default, setting_typeset";

    @Select("SELECT " + COLS + " FROM chv_settings WHERE setting_id = #{id}")
    Optional<Setting> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_settings")
    List<Setting> findAll();

    @Select("SELECT COUNT(*) FROM chv_settings")
    long count();

    @Select("SELECT " + COLS + " FROM chv_settings WHERE setting_name = #{settingName} LIMIT 1")
    Optional<Setting> findBySettingName(String settingName);

    @Insert("INSERT INTO chv_settings (setting_name, setting_value, setting_default, setting_typeset) "
            + "VALUES (#{settingName}, #{settingValue}, #{settingDefault}, #{settingTypeset})")
    @Options(useGeneratedKeys = true, keyProperty = "settingId")
    int insert(Setting setting);

    @Update("UPDATE chv_settings SET setting_value = #{settingValue} WHERE setting_id = #{settingId}")
    int update(Setting setting);

    default Setting save(Setting setting) {
        if (setting.getSettingId() == null) {
            insert(setting);
        } else {
            update(setting);
        }
        return setting;
    }
}

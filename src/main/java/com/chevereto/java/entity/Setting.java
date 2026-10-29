package com.chevereto.java.entity;


/** 对应 Chevereto 原版表 chv_settings（setting_name / setting_value / setting_default / setting_typeset） */
public class Setting {

    private Long settingId;

    private String settingName;

    private String settingValue;

    private String settingDefault;

    /** string / bool（与原版枚举一致） */
    private String settingTypeset = "string";

    public Long getSettingId() { return settingId; }
    public void setSettingId(Long settingId) { this.settingId = settingId; }
    public String getSettingName() { return settingName; }
    public void setSettingName(String settingName) { this.settingName = settingName; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
    public String getSettingDefault() { return settingDefault; }
    public void setSettingDefault(String settingDefault) { this.settingDefault = settingDefault; }
    public String getSettingTypeset() { return settingTypeset; }
    public void setSettingTypeset(String settingTypeset) { this.settingTypeset = settingTypeset; }
}

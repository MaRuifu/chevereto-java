package com.chevereto.java.entity;


import java.time.LocalDateTime;

/**
 * 对应 Chevereto 原版表 chv_users。
 * user_status 枚举与原版一致：valid / awaiting-confirmation / awaiting-email / banned。
 * 密码不存本表，存 chv_login_passwords（与原版一致）。
 */
public class User {

    private Long userId;

    private String userUsername;

    private String userName;

    private String userEmail;

    private LocalDateTime userDate = LocalDateTime.now();

    private LocalDateTime userDateGmt = LocalDateTime.now();

    private String userBio;

    private String userWebsite;

    private String userTimezone = "Asia/Shanghai";

    private String userLanguage = "zh-CN";

    private String userStatus = "valid";

    private boolean userIsAdmin = false;

    private boolean userIsPrivate = false;

    private boolean userShowNsfwListings = false;

    private long userImageCount = 0;

    private long userAlbumCount = 0;

    private String userRegistrationIp = "";

    private String userAvatarFilename;

    private String passwordHash;

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getUserUsername() { return userUsername; }
    public void setUserUsername(String userUsername) { this.userUsername = userUsername; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }
    public LocalDateTime getUserDate() { return userDate; }
    public void setUserDate(LocalDateTime userDate) { this.userDate = userDate; }
    public LocalDateTime getUserDateGmt() { return userDateGmt; }
    public void setUserDateGmt(LocalDateTime userDateGmt) { this.userDateGmt = userDateGmt; }
    public String getUserBio() { return userBio; }
    public void setUserBio(String userBio) { this.userBio = userBio; }
    public String getUserWebsite() { return userWebsite; }
    public void setUserWebsite(String userWebsite) { this.userWebsite = userWebsite; }
    public String getUserTimezone() { return userTimezone; }
    public void setUserTimezone(String userTimezone) { this.userTimezone = userTimezone; }
    public String getUserLanguage() { return userLanguage; }
    public void setUserLanguage(String userLanguage) { this.userLanguage = userLanguage; }
    public String getUserStatus() { return userStatus; }
    public void setUserStatus(String userStatus) { this.userStatus = userStatus; }
    public boolean isUserIsAdmin() { return userIsAdmin; }
    public void setUserIsAdmin(boolean userIsAdmin) { this.userIsAdmin = userIsAdmin; }
    public boolean isUserIsPrivate() { return userIsPrivate; }
    public void setUserIsPrivate(boolean userIsPrivate) { this.userIsPrivate = userIsPrivate; }
    public boolean isUserShowNsfwListings() { return userShowNsfwListings; }
    public void setUserShowNsfwListings(boolean userShowNsfwListings) { this.userShowNsfwListings = userShowNsfwListings; }
    public long getUserImageCount() { return userImageCount; }
    public void setUserImageCount(long userImageCount) { this.userImageCount = userImageCount; }
    public long getUserAlbumCount() { return userAlbumCount; }
    public void setUserAlbumCount(long userAlbumCount) { this.userAlbumCount = userAlbumCount; }
    public String getUserRegistrationIp() { return userRegistrationIp; }
    public void setUserRegistrationIp(String userRegistrationIp) { this.userRegistrationIp = userRegistrationIp; }
    public String getUserAvatarFilename() { return userAvatarFilename; }
    public void setUserAvatarFilename(String userAvatarFilename) { this.userAvatarFilename = userAvatarFilename; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public boolean isBanned() { return "banned".equals(userStatus); }
}

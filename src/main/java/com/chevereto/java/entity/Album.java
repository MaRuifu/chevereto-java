package com.chevereto.java.entity;


import java.time.LocalDateTime;

/**
 * 对应 Chevereto 原版表 chv_albums。
 * album_privacy 枚举与原版一致：public / password / private / private_but_link / custom。
 */
public class Album {

    private Long albumId;

    private String albumName;

    private Long albumUserId;

    private LocalDateTime albumDate = LocalDateTime.now();

    private LocalDateTime albumDateGmt = LocalDateTime.now();

    private String albumCreationIp = "";

    private String albumPrivacy = "public";

    private String albumDescription;

    private String albumPassword;

    private long albumImageCount = 0;

    private long albumViews = 0;

    private long albumLikes = 0;

    private Long albumCoverId;

    private Long albumParentId;

    private String idEncoded;
    private User user;
    private String coverUrl;

    public Long getAlbumId() { return albumId; }
    public void setAlbumId(Long albumId) { this.albumId = albumId; }
    public String getAlbumName() { return albumName; }
    public void setAlbumName(String albumName) { this.albumName = albumName; }
    public Long getAlbumUserId() { return albumUserId; }
    public void setAlbumUserId(Long albumUserId) { this.albumUserId = albumUserId; }
    public LocalDateTime getAlbumDate() { return albumDate; }
    public void setAlbumDate(LocalDateTime albumDate) { this.albumDate = albumDate; }
    public LocalDateTime getAlbumDateGmt() { return albumDateGmt; }
    public void setAlbumDateGmt(LocalDateTime albumDateGmt) { this.albumDateGmt = albumDateGmt; }
    public String getAlbumCreationIp() { return albumCreationIp; }
    public void setAlbumCreationIp(String albumCreationIp) { this.albumCreationIp = albumCreationIp; }
    public String getAlbumPrivacy() { return albumPrivacy; }
    public void setAlbumPrivacy(String albumPrivacy) { this.albumPrivacy = albumPrivacy; }
    public String getAlbumDescription() { return albumDescription; }
    public void setAlbumDescription(String albumDescription) { this.albumDescription = albumDescription; }
    public String getAlbumPassword() { return albumPassword; }
    public void setAlbumPassword(String albumPassword) { this.albumPassword = albumPassword; }
    public long getAlbumImageCount() { return albumImageCount; }
    public void setAlbumImageCount(long albumImageCount) { this.albumImageCount = albumImageCount; }
    public long getAlbumViews() { return albumViews; }
    public void setAlbumViews(long albumViews) { this.albumViews = albumViews; }
    public long getAlbumLikes() { return albumLikes; }
    public void setAlbumLikes(long albumLikes) { this.albumLikes = albumLikes; }
    public Long getAlbumCoverId() { return albumCoverId; }
    public void setAlbumCoverId(Long albumCoverId) { this.albumCoverId = albumCoverId; }
    public Long getAlbumParentId() { return albumParentId; }
    public void setAlbumParentId(Long albumParentId) { this.albumParentId = albumParentId; }
    public String getIdEncoded() { return idEncoded; }
    public void setIdEncoded(String idEncoded) { this.idEncoded = idEncoded; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
    public String getCoverUrl() { return coverUrl; }
    public void setCoverUrl(String coverUrl) { this.coverUrl = coverUrl; }
}

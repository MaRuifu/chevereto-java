package com.chevereto.java.entity;


/** 对应 Chevereto 原版表 chv_categories */
public class Category {

    private Long categoryId;

    private String categoryName;

    private String categoryUrlKey;

    private String categoryDescription;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getCategoryUrlKey() { return categoryUrlKey; }
    public void setCategoryUrlKey(String categoryUrlKey) { this.categoryUrlKey = categoryUrlKey; }
    public String getCategoryDescription() { return categoryDescription; }
    public void setCategoryDescription(String categoryDescription) { this.categoryDescription = categoryDescription; }
}

package com.chevereto.java.repository;

import com.chevereto.java.entity.Category;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;
import java.util.Optional;

/** chv_categories —— MyBatis 注解 SQL（无 XML） */
@Mapper
public interface CategoryRepository {

    String COLS = "category_id, category_name, category_url_key, category_description";

    @Select("SELECT " + COLS + " FROM chv_categories WHERE category_id = #{id}")
    Optional<Category> findById(Long id);

    @Select("SELECT " + COLS + " FROM chv_categories ORDER BY category_id ASC")
    List<Category> findAll();

    @Select("SELECT COUNT(*) FROM chv_categories")
    long count();

    default List<Category> findAllById(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return findByIds(ids);
    }

    @Select("<script>SELECT " + COLS + " FROM chv_categories WHERE category_id IN "
            + "<foreach item='id' collection='ids' open='(' separator=',' close=')'>#{id}</foreach>"
            + "</script>")
    List<Category> findByIds(@Param("ids") List<Long> ids);

    @Insert("INSERT INTO chv_categories (category_name, category_url_key, category_description) "
            + "VALUES (#{categoryName}, #{categoryUrlKey}, #{categoryDescription})")
    @Options(useGeneratedKeys = true, keyProperty = "categoryId")
    int insert(Category category);

    @Update("UPDATE chv_categories SET "
            + "category_name = #{categoryName}, category_url_key = #{categoryUrlKey}, "
            + "category_description = #{categoryDescription} "
            + "WHERE category_id = #{categoryId}")
    int update(Category category);

    default Category save(Category category) {
        if (category.getCategoryId() == null) {
            insert(category);
        } else {
            update(category);
        }
        return category;
    }

    @Delete("DELETE FROM chv_categories WHERE category_id = #{id}")
    int deleteById(Long id);

    default void delete(Category category) {
        deleteById(category.getCategoryId());
    }
}

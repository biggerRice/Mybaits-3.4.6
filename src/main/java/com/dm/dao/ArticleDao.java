package com.dm.dao;

import com.dm.entity.ArticleEntity;
import org.apache.ibatis.annotations.Param;

public interface ArticleDao {

    ArticleEntity getArticleById (@Param("articleId") int articleId);

}
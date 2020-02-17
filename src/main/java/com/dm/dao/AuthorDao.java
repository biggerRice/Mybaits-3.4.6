package com.dm.dao;


import com.dm.entity.AuthorEntity;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface AuthorDao {

    int addAuthor(AuthorEntity authorEntity);

    Map findAuthorMap(@Param("authorId") int authorId);

    AuthorEntity getAuthorById(@Param("authorId") int authorId);

}
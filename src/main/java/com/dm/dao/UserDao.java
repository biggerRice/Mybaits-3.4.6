package com.dm.dao;

import com.dm.entity.User;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

public interface UserDao {

    Map findMap (@Param("userId") int userId);

    User findUserById (@Param("userId") int userId);

    void insertUser (User user);

    void updateUser (User user);

    void deleteUser (User user);


}
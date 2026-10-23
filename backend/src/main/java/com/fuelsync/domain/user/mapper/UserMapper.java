package com.fuelsync.domain.user.mapper;

import com.fuelsync.domain.user.model.UserAccount;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Optional;

@Mapper
public interface UserMapper {

    Optional<UserAccount> findByUsername(@Param("username") String username);
}

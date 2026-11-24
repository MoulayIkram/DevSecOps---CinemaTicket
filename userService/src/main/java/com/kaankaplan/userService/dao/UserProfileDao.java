package com.kaankaplan.userService.dao;

import com.kaankaplan.userService.entity.UserProfile;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserProfileDao extends MongoRepository<UserProfile, String> {
}

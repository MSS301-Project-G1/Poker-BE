package com.msspoker.socialservice.mapper;

import com.msspoker.socialservice.dto.response.SocialDtos.FriendRequestView;
import com.msspoker.socialservice.entity.FriendRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface SocialMapper {
  FriendRequestView request(FriendRequest entity);
}

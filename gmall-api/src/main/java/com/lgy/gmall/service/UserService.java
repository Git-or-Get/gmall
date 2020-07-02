package com.lgy.gmall.service;

import com.lgy.gmall.bean.UmsMember;
import com.lgy.gmall.bean.UmsMemberReceiveAddress;

import java.util.List;

public interface UserService {

    List<UmsMember> getAllUser();

    List<UmsMemberReceiveAddress> getReceiveAddressByMemberId(String memberId);
}

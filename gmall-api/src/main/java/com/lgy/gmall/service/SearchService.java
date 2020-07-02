package com.lgy.gmall.service;

import com.lgy.gmall.bean.PmsSearchParam;
import com.lgy.gmall.bean.PmsSearchSkuInfo;

import java.util.List;

public interface SearchService {
    List<PmsSearchSkuInfo> list(PmsSearchParam pmsSearchParam);
}

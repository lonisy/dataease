package io.dataease.datasource.service;

import com.google.gson.Gson;
import io.dataease.base.domain.*;
import io.dataease.base.mapper.*;
import io.dataease.datasource.dto.MysqlConfigrationDTO;
import io.dataease.datasource.provider.DatasourceProvider;
import io.dataease.datasource.provider.JdbcProvider;
import io.dataease.datasource.provider.ProviderFactory;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(rollbackFor = Exception.class)
public class DatasourceService {

    @Resource
    private DatasourceMapper datasourceMapper;

    public Datasource addDatasource(Datasource datasource) {
        long currentTimeMillis = System.currentTimeMillis();
        datasource.setId(UUID.randomUUID().toString());
        datasource.setUpdateTime(currentTimeMillis);
        datasource.setCreateTime(currentTimeMillis);
        datasourceMapper.insertSelective(datasource);
        return datasource;
    }

    public List<Datasource> getDatasourceList(Datasource request)throws Exception{
        DatasourceExample example = new DatasourceExample();
        DatasourceExample.Criteria criteria = example.createCriteria();
        if (StringUtils.isNotBlank(request.getName())) {
            criteria.andNameLike(StringUtils.wrapIfMissing(request.getName(), "%"));
        }
        if (StringUtils.isNotBlank(request.getType())) {
            criteria.andTypeEqualTo(request.getType());
        }
        example.setOrderByClause("update_time desc");
        return datasourceMapper.selectByExample(example);
    }

    public void deleteDatasource(String datasourceId) {
        datasourceMapper.deleteByPrimaryKey(datasourceId);
    }

    public void updateDatasource(Datasource datasource) {
        datasource.setCreateTime(null);
        datasource.setUpdateTime(System.currentTimeMillis());
        datasourceMapper.updateByPrimaryKeySelective(datasource);
    }

    public void validate(Datasource datasource)throws Exception {
        DatasourceProvider datasourceProvider = ProviderFactory.getProvider(datasource.getType());
        datasourceProvider.setDataSourceConfigration(datasource.getConfiguration());
        datasourceProvider.test();
    }



}
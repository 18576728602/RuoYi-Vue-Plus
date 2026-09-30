package org.dromara.budget.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.BudgetCategory;
import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.budget.domain.bo.BudgetCategoryBo;
import org.dromara.budget.domain.vo.BudgetCategoryVo;
import org.dromara.budget.mapper.BudgetCategoryMapper;
import org.dromara.budget.mapper.BudgetSubjectMasterMapper;
import org.dromara.budget.service.IBudgetCategoryService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 预算业务分类Service业务层处理
 *
 * @author Lion Li
 * @date 2026-09-28
 */
@RequiredArgsConstructor
@Service
public class BudgetCategoryServiceImpl implements IBudgetCategoryService {

    private final BudgetCategoryMapper baseMapper;

    private final BudgetSubjectMasterMapper subjectMasterMapper;

    @Override
    public TableDataInfo<BudgetCategoryVo> queryPageList(BudgetCategoryBo bo, PageQuery pageQuery) {
        IPage<BudgetCategoryVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        fillSubjectCount(page.getRecords());
        return TableDataInfo.build(page);
    }

    @Override
    public List<BudgetCategoryVo> queryList(BudgetCategoryBo bo) {
        List<BudgetCategoryVo> list = baseMapper.selectVoList(buildWrapper(bo));
        fillSubjectCount(list);
        return list;
    }

    @Override
    public BudgetCategoryVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public String nextCode() {
        List<BudgetCategory> cats = baseMapper.selectList(new LambdaQueryWrapper<BudgetCategory>()
            .select(BudgetCategory::getCategoryCode));
        List<String> codes = cats.stream()
            .map(BudgetCategory::getCategoryCode)
            .filter(StrUtil::isNotBlank)
            .toList();
        long maxNum = 0;
        int width = 2;
        String prefix = "";
        for (String c : codes) {
            Matcher m = Pattern.compile("^([a-zA-Z]*)(\\d+)$").matcher(c.trim());
            if (m.matches()) {
                prefix = m.group(1);
                width = Math.max(width, m.group(2).length());
                maxNum = Math.max(maxNum, Long.parseLong(m.group(2)));
            }
        }
        return prefix + String.format("%0" + width + "d", maxNum + 1);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean insertByBo(BudgetCategoryBo bo) {
        if (bo.getCategoryCode() == null || bo.getCategoryCode().isBlank()) {
            bo.setCategoryCode(nextCode());
        }
        if (existsCode(bo.getCategoryCode(), null)) {
            throw new ServiceException("分类编码[" + bo.getCategoryCode() + "]已存在");
        }
        BudgetCategory entity = MapstructUtils.convert(bo, BudgetCategory.class);
        if (entity.getSort() == null) {
            entity.setSort(1);
        }
        return baseMapper.insert(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean updateByBo(BudgetCategoryBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("分类ID不能为空");
        }
        if (StrUtil.isNotBlank(bo.getCategoryCode()) && existsCode(bo.getCategoryCode(), bo.getId())) {
            throw new ServiceException("分类编码[" + bo.getCategoryCode() + "]已存在");
        }
        BudgetCategory entity = MapstructUtils.convert(bo, BudgetCategory.class);
        return baseMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Boolean deleteWithValidByIds(Collection<Long> ids) {
        if (CollUtil.isEmpty(ids)) {
            return Boolean.FALSE;
        }
        for (Long id : ids) {
            BudgetCategory cat = baseMapper.selectById(id);
            if (cat == null) {
                continue;
            }
            Long cnt = subjectMasterMapper.selectCount(new LambdaQueryWrapper<BudgetSubjectMaster>()
                .eq(BudgetSubjectMaster::getCategoryCode, cat.getCategoryCode())
                .eq(BudgetSubjectMaster::getValidFlag, "1"));
            if (cnt != null && cnt > 0) {
                throw new ServiceException("分类[" + cat.getCategoryName() + "]下仍有" + cnt + "个有效明细科目，不能删除");
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    private boolean existsCode(String code, Long excludeId) {
        LambdaQueryWrapper<BudgetCategory> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetCategory::getCategoryCode, code)
            .ne(excludeId != null, BudgetCategory::getId, excludeId);
        return baseMapper.selectCount(qw) > 0;
    }

    /** 回填每个分类下平铺的有效明细科目数 */
    private void fillSubjectCount(List<BudgetCategoryVo> list) {
        if (CollUtil.isEmpty(list)) {
            return;
        }
        List<String> codes = list.stream().map(BudgetCategoryVo::getCategoryCode)
            .filter(StrUtil::isNotBlank).toList();
        if (CollUtil.isEmpty(codes)) {
            return;
        }
        List<BudgetSubjectMaster> subs = subjectMasterMapper.selectList(new LambdaQueryWrapper<BudgetSubjectMaster>()
            .in(BudgetSubjectMaster::getCategoryCode, codes)
            .eq(BudgetSubjectMaster::getValidFlag, "1"));
        Map<String, Integer> cntMap = new HashMap<>();
        for (BudgetSubjectMaster s : subs) {
            if (s.getCategoryCode() != null) {
                cntMap.merge(s.getCategoryCode(), 1, Integer::sum);
            }
        }
        for (BudgetCategoryVo vo : list) {
            if (vo.getCategoryCode() != null) {
                vo.setSubjectCount(cntMap.getOrDefault(vo.getCategoryCode(), 0));
            }
        }
    }

    private LambdaQueryWrapper<BudgetCategory> buildWrapper(BudgetCategoryBo bo) {
        LambdaQueryWrapper<BudgetCategory> qw = new LambdaQueryWrapper<>();
        if (bo != null) {
            qw.like(StrUtil.isNotBlank(bo.getCategoryCode()), BudgetCategory::getCategoryCode, bo.getCategoryCode());
            qw.like(StrUtil.isNotBlank(bo.getCategoryName()), BudgetCategory::getCategoryName, bo.getCategoryName());
        }
        qw.orderByAsc(BudgetCategory::getSort);
        qw.orderByAsc(BudgetCategory::getId);
        return qw;
    }
}
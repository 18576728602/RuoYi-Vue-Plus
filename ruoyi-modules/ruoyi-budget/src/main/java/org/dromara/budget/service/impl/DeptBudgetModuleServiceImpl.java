package org.dromara.budget.service.impl;

import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.core.utils.StringUtils;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.dromara.common.mybatis.core.page.PageQuery;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.dromara.budget.domain.bo.DeptBudgetModuleBo;
import org.dromara.budget.domain.vo.DeptBudgetModuleVo;
import org.dromara.budget.domain.DeptBudgetModule;
import org.dromara.budget.mapper.DeptBudgetModuleMapper;
import org.dromara.budget.service.IDeptBudgetModuleService;

import java.util.List;
import java.util.Map;
import java.util.Collection;

/**
 * 部门-预算板块映射Service业务层处理
 *
 * @author Lion Li
 * @date 2026-08-29
 */
@Slf4j
@RequiredArgsConstructor
@Service
public class DeptBudgetModuleServiceImpl implements IDeptBudgetModuleService {

    private final DeptBudgetModuleMapper baseMapper;

    /**
     * 查询部门-预算板块映射
     *
     * @param id 主键
     * @return 部门-预算板块映射
     */
    @Override
    public DeptBudgetModuleVo queryById(Long id){
        return baseMapper.selectVoById(id);
    }

    /**
     * 分页查询部门-预算板块映射列表
     *
     * @param bo        查询条件
     * @param pageQuery 分页参数
     * @return 部门-预算板块映射分页列表
     */
    @Override
    public TableDataInfo<DeptBudgetModuleVo> queryPageList(DeptBudgetModuleBo bo, PageQuery pageQuery) {
        LambdaQueryWrapper<DeptBudgetModule> lqw = buildQueryWrapper(bo);
        Page<DeptBudgetModuleVo> result = baseMapper.selectVoPage(pageQuery.build(), lqw);
        return TableDataInfo.build(result);
    }

    /**
     * 查询符合条件的部门-预算板块映射列表
     *
     * @param bo 查询条件
     * @return 部门-预算板块映射列表
     */
    @Override
    public List<DeptBudgetModuleVo> queryList(DeptBudgetModuleBo bo) {
        LambdaQueryWrapper<DeptBudgetModule> lqw = buildQueryWrapper(bo);
        return baseMapper.selectVoList(lqw);
    }

    private LambdaQueryWrapper<DeptBudgetModule> buildQueryWrapper(DeptBudgetModuleBo bo) {
        Map<String, Object> params = bo.getParams();
        LambdaQueryWrapper<DeptBudgetModule> lqw = Wrappers.lambdaQuery();
        lqw.orderByAsc(DeptBudgetModule::getId);
        lqw.eq(bo.getDeptId() != null, DeptBudgetModule::getDeptId, bo.getDeptId());
        lqw.eq(StringUtils.isNotBlank(bo.getTemplateCode()), DeptBudgetModule::getTemplateCode, bo.getTemplateCode());
        lqw.eq(StringUtils.isNotBlank(bo.getItemCodes()), DeptBudgetModule::getItemCodes, bo.getItemCodes());
        return lqw;
    }

    /**
     * 新增部门-预算板块映射
     *
     * @param bo 部门-预算板块映射
     * @return 是否新增成功
     */
    @Override
    public Boolean insertByBo(DeptBudgetModuleBo bo) {
        DeptBudgetModule add = MapstructUtils.convert(bo, DeptBudgetModule.class);
        validEntityBeforeSave(add);
        boolean flag = baseMapper.insert(add) > 0;
        if (flag) {
            bo.setId(add.getId());
        }
        return flag;
    }

    /**
     * 修改部门-预算板块映射
     *
     * @param bo 部门-预算板块映射
     * @return 是否修改成功
     */
    @Override
    public Boolean updateByBo(DeptBudgetModuleBo bo) {
        DeptBudgetModule update = MapstructUtils.convert(bo, DeptBudgetModule.class);
        validEntityBeforeSave(update);
        return baseMapper.updateById(update) > 0;
    }

    /**
     * 保存前的数据校验
     */
    private void validEntityBeforeSave(DeptBudgetModule entity){
        //TODO 做一些数据校验,如唯一约束
    }

    /**
     * 校验并批量删除部门-预算板块映射信息
     *
     * @param ids     待删除的主键集合
     * @param isValid 是否进行有效性校验
     * @return 是否删除成功
     */
    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if(isValid){
            //TODO 做一些业务上的校验,判断是否需要校验
        }
        return baseMapper.deleteByIds(ids) > 0;
    }
}

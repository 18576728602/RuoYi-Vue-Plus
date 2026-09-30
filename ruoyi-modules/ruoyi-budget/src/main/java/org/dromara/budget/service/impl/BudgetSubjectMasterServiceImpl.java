package org.dromara.budget.service.impl;

import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.RequiredArgsConstructor;
import org.dromara.budget.domain.BudgetSubjectMaster;
import org.dromara.budget.domain.BudgetTemplateItem;
import org.dromara.budget.domain.BudgetCategory;
import org.dromara.budget.domain.bo.BudgetSubjectMasterBo;
import org.dromara.budget.domain.vo.BudgetSubjectMasterVo;
import org.dromara.budget.mapper.BudgetCategoryMapper;
import org.dromara.budget.mapper.BudgetSubjectMasterMapper;
import org.dromara.budget.mapper.BudgetTemplateItemMapper;
import org.dromara.budget.service.IBudgetSubjectMasterService;
import org.dromara.common.core.exception.ServiceException;
import org.dromara.common.core.utils.MapstructUtils;
import org.dromara.common.mybatis.core.page.PageQuery;
import org.dromara.common.mybatis.core.page.TableDataInfo;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 预算科目主数据 Service实现
 *
 * @author Lion Li
 * @date 2026-09-15
 */
@RequiredArgsConstructor
@Service
public class BudgetSubjectMasterServiceImpl implements IBudgetSubjectMasterService {

    private final BudgetSubjectMasterMapper baseMapper;

    private final BudgetTemplateItemMapper templateItemMapper;

    private final BudgetCategoryMapper categoryMapper;

    @Override
    public BudgetSubjectMasterVo queryById(Long id) {
        return baseMapper.selectVoById(id);
    }

    @Override
    public TableDataInfo<BudgetSubjectMasterVo> queryPageList(BudgetSubjectMasterBo bo, PageQuery pageQuery) {
        IPage<BudgetSubjectMasterVo> page = baseMapper.selectVoPage(pageQuery.build(), buildWrapper(bo));
        return TableDataInfo.build(page);
    }

    @Override
    public List<BudgetSubjectMasterVo> queryList(BudgetSubjectMasterBo bo) {
        List<BudgetSubjectMasterVo> list = baseMapper.selectVoList(buildWrapper(bo));
        if (list != null && !list.isEmpty()) {
            // 一次性统计全部主数据的挂接引用数(budget_template_item.ref_subject_id 行数, 含方案挂接 + 预算表挂载),
            // 分组回填到每个节点, 避免 N+1 查询
            List<BudgetTemplateItem> refs = templateItemMapper.selectList(
                new LambdaQueryWrapper<BudgetTemplateItem>().isNotNull(BudgetTemplateItem::getRefSubjectId));
            Map<Long, Long> countMap = new HashMap<>();
            for (BudgetTemplateItem it : refs) {
                if (it.getRefSubjectId() != null) {
                    countMap.merge(it.getRefSubjectId(), 1L, Long::sum);
                }
            }
            for (BudgetSubjectMasterVo vo : list) {
                if (vo.getId() != null) {
                    vo.setRefCount(countMap.getOrDefault(vo.getId(), 0L));
                }
            }
        }
        return list;
    }

    @Override
    public String nextCode(String templateCode, String parentCode, String subjectType, Long orgId) {
        LambdaQueryWrapper<BudgetSubjectMaster> qw = new LambdaQueryWrapper<>();
        if (StrUtil.isBlank(parentCode)) {
            // 一级科目: 同级(同表)顶层编码
            qw.eq(StrUtil.isNotBlank(templateCode), BudgetSubjectMaster::getTemplateCode, templateCode)
                .eq(StrUtil.isNotBlank(subjectType), BudgetSubjectMaster::getSubjectType, subjectType)
                .eq(ObjectUtil.isNotNull(orgId), BudgetSubjectMaster::getOrgId, orgId)
                .and(w -> w.isNull(BudgetSubjectMaster::getParentCode).or().eq(BudgetSubjectMaster::getParentCode, ""));
        } else {
            // 下级科目: 同父级下编码
            qw.eq(BudgetSubjectMaster::getParentCode, parentCode)
                .eq(StrUtil.isNotBlank(subjectType), BudgetSubjectMaster::getSubjectType, subjectType)
                .eq(ObjectUtil.isNotNull(orgId), BudgetSubjectMaster::getOrgId, orgId);
        }
        List<String> codes = baseMapper.selectList(qw).stream()
            .map(BudgetSubjectMaster::getSubjectCode)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toList());
        String code = StrUtil.isBlank(parentCode) ? nextRootCode(codes) : nextChildCode(parentCode, codes);
        // 唯一性兜底: 在(同表/同父级 + 类型 + 归属)内若已存在则递增
        while (codeExists(code, parentCode, subjectType, orgId)) {
            code = bumpCode(code);
        }
        return code;
    }

    /** 一级科目编码: 数字尾部最大值+1, 兼容 "Z01" 类前缀 */
    private String nextRootCode(List<String> codes) {
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

    /** 下级科目编码: 父编码 + 两位序号, 自动递增 */
    private String nextChildCode(String parentCode, List<String> codes) {
        int maxSeq = 0;
        for (String c : codes) {
            if (c.startsWith(parentCode)) {
                String tail = c.substring(parentCode.length());
                try {
                    maxSeq = Math.max(maxSeq, Integer.parseInt(tail));
                } catch (NumberFormatException ignore) {
                    // 忽略非纯数字后缀
                }
            }
        }
        return parentCode + String.format("%02d", maxSeq + 1);
    }

    private boolean codeExists(String code, String parentCode, String subjectType, Long orgId) {
        LambdaQueryWrapper<BudgetSubjectMaster> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetSubjectMaster::getSubjectCode, code)
            .eq(BudgetSubjectMaster::getSubjectType, StrUtil.blankToDefault(subjectType, "SYS"))
            .eq(ObjectUtil.isNotNull(orgId), BudgetSubjectMaster::getOrgId, orgId);
        return baseMapper.selectCount(qw) > 0;
    }

    /** 递增编码尾部数字(保持位数) */
    private String bumpCode(String code) {
        Matcher m = Pattern.compile("^(.*?)(\\d+)$").matcher(code);
        if (!m.matches()) {
            return code + "x";
        }
        String head = m.group(1);
        String tail = m.group(2);
        long next = Long.parseLong(tail) + 1;
        return head + String.format("%0" + tail.length() + "d", next);
    }

    @Override
    public Boolean insertByBo(BudgetSubjectMasterBo bo) {
        fillLevelAndDefaults(bo);
        if (existsCode(bo, null)) {
            throw new ServiceException("科目编码[" + bo.getSubjectCode() + "]已存在");
        }
        BudgetSubjectMaster entity = MapstructUtils.convert(bo, BudgetSubjectMaster.class);
        boolean ok = baseMapper.insert(entity) > 0;
        if (ok) {
            // 新增子科目后，把其父级提升为分组表头(HEAD)，保持结构行数据化
            promoteParentToHead(entity.getParentCode(), entity.getSubjectType(), entity.getOrgId());
        }
        return ok;
    }

    @Override
    public Boolean updateByBo(BudgetSubjectMasterBo bo) {
        if (bo.getId() == null) {
            throw new ServiceException("科目ID不能为空");
        }
        if (existsCode(bo, bo.getId())) {
            throw new ServiceException("科目编码[" + bo.getSubjectCode() + "]已存在");
        }
        // 记录编辑前的编码和版本号，用于检测编码变更并级联更新子级和挂接快照
        BudgetSubjectMaster old = baseMapper.selectById(bo.getId());
        if (old == null) {
            throw new ServiceException("科目不存在或已被删除");
        }
        String oldCode = old.getSubjectCode();
        fillLevelAndDefaults(bo);
        BudgetSubjectMaster entity = MapstructUtils.convert(bo, BudgetSubjectMaster.class);
        // 关键：乐观锁 version 必须从旧实体拷贝，否则 updateById 的 WHERE version=null 永远匹配不到行
        entity.setVersion(old.getVersion());
        boolean ok = baseMapper.updateById(entity) > 0;
        if (ok && StrUtil.isNotBlank(oldCode) && StrUtil.isNotBlank(bo.getSubjectCode()) && !oldCode.equals(bo.getSubjectCode())) {
            cascadeCodeChange(oldCode, bo.getSubjectCode(), bo.getSubjectType(), bo.getOrgId());
        }
        return ok;
    }

    /**
     * 编码变更级联更新（含所有后代）：
     * 1) 主数据：所有后代科目的 subject_code / parent_code 前缀替换（旧编码→新编码）
     * 2) 挂接快照：budget_template_item 中所有后代的 item_code / parent_code 前缀替换
     *
     * 说明：因为编码具有层级含义（前缀=父级编码），父级编码变更时，
     * 所有子子孙孙的编码前缀都要跟着变，否则会出现编码与层级不匹配的孤儿数据。
     */
    private void cascadeCodeChange(String oldCode, String newCode, String subjectType, Long orgId) {
        // 1) 主数据：级联更新所有后代的 subject_code 和 parent_code 前缀
        baseMapper.cascadeUpdateDescendantCode(oldCode, newCode,
            StrUtil.blankToDefault(subjectType, "SYS"), orgId);
        // 2) 挂接快照：级联更新所有后代的 item_code 和 parent_code 前缀
        templateItemMapper.cascadeUpdateDescendantCode(oldCode, newCode);
    }

    @Override
    public Boolean deleteWithValidByIds(Collection<Long> ids, Boolean isValid) {
        if (ObjectUtil.isEmpty(ids)) {
            return Boolean.FALSE;
        }
        if (Boolean.TRUE.equals(isValid)) {
            for (Long id : ids) {
                BudgetSubjectMasterVo vo = queryById(id);
                if (vo == null) {
                    continue;
                }
                // 守卫：仅已停用的科目允许删除，未停用必须先停用
                if (!"0".equals(vo.getValidFlag())) {
                    throw new ServiceException("科目[" + vo.getSubjectName() + "]尚未停用，请先停用后再删除");
                }
                if (StrUtil.isNotBlank(vo.getSubjectCode())
                    && hasChildren(vo.getSubjectCode(), vo.getSubjectType(), vo.getOrgId())) {
                    throw new ServiceException("科目[" + vo.getSubjectName() + "]存在下级科目，不能删除");
                }
                // 守卫：存在有效挂引用的科目禁止删除，避免预算表出现悬空明细
                long refs = countRefs(id);
                if (refs > 0) {
                    throw new ServiceException("科目[" + vo.getSubjectName() + "]已被 " + refs + " 个方案/预算表挂接，请先解除挂接后再删除");
                }
            }
        }
        return baseMapper.deleteByIds(ids) > 0;
    }

    private boolean hasChildren(String parentCode, String subjectType, Long orgId) {
        LambdaQueryWrapper<BudgetSubjectMaster> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetSubjectMaster::getParentCode, parentCode)
            .eq(StrUtil.isNotBlank(subjectType), BudgetSubjectMaster::getSubjectType, subjectType)
            .eq(ObjectUtil.isNotNull(orgId), BudgetSubjectMaster::getOrgId, orgId);
        return baseMapper.selectCount(qw) > 0;
    }

    /** 新增子科目后，把父级提升为分组表头(HEAD)；父级为汇总/只读基准则不动 */
    private void promoteParentToHead(String parentCode, String subjectType, Long orgId) {
        if (StrUtil.isBlank(parentCode)) {
            return;
        }
        BudgetSubjectMaster parent = baseMapper.selectOne(new LambdaQueryWrapper<BudgetSubjectMaster>()
            .eq(BudgetSubjectMaster::getSubjectCode, parentCode)
            .eq(StrUtil.isNotBlank(subjectType), BudgetSubjectMaster::getSubjectType, subjectType)
            .eq(ObjectUtil.isNotNull(orgId), BudgetSubjectMaster::getOrgId, orgId)
            .last("LIMIT 1"));
        if (parent == null || parent.getId() == null) {
            return;
        }
        if ("SUM".equals(parent.getRowType()) || "REF".equals(parent.getRowType())) {
            return;
        }
        if (!"HEAD".equals(parent.getRowType())) {
            baseMapper.update(null, new LambdaUpdateWrapper<BudgetSubjectMaster>()
                .eq(BudgetSubjectMaster::getId, parent.getId())
                .set(BudgetSubjectMaster::getRowType, "HEAD"));
        }
    }

    private boolean existsCode(BudgetSubjectMasterBo bo, Long excludeId) {
        LambdaQueryWrapper<BudgetSubjectMaster> qw = new LambdaQueryWrapper<>();
        qw.eq(BudgetSubjectMaster::getSubjectCode, bo.getSubjectCode())
            .eq(BudgetSubjectMaster::getSubjectType, StrUtil.blankToDefault(bo.getSubjectType(), "SYS"))
            .eq(ObjectUtil.isNotNull(bo.getOrgId()), BudgetSubjectMaster::getOrgId, bo.getOrgId())
            .ne(excludeId != null, BudgetSubjectMaster::getId, excludeId);
        return baseMapper.selectCount(qw) > 0;
    }

    /** 依据父级自动推导层级、默认值 */
    private void fillLevelAndDefaults(BudgetSubjectMasterBo bo) {
        if (StrUtil.isBlank(bo.getSubjectType())) {
            bo.setSubjectType("SYS");
        }
        if (bo.getBudgetYear() == null) {
            bo.setBudgetYear(2027);
        }
        if (StrUtil.isBlank(bo.getValidFlag())) {
            bo.setValidFlag("1");
        }
        if (bo.getSort() == null) {
            bo.setSort(1);
        }
        // 新建默认行类型=明细(ITEM)；编辑不覆盖，避免把 HEAD/SUM/REF 回退成 ITEM
        if (bo.getId() == null && StrUtil.isBlank(bo.getRowType())) {
            bo.setRowType("ITEM");
        }
        if (StrUtil.isBlank(bo.getParentCode())) {
            bo.setParentCode(null);
            if (bo.getLevel() == null || bo.getLevel() < 1) {
                bo.setLevel(1);
            }
            return;
        }
        BudgetSubjectMaster parent = baseMapper.selectOne(new LambdaQueryWrapper<BudgetSubjectMaster>()
            .eq(BudgetSubjectMaster::getSubjectCode, bo.getParentCode())
            .eq(BudgetSubjectMaster::getSubjectType, bo.getSubjectType())
            .eq(ObjectUtil.isNotNull(bo.getOrgId()), BudgetSubjectMaster::getOrgId, bo.getOrgId())
            .last("LIMIT 1"));
        if (parent == null) {
            // 方案A：明细平铺在分类下，parentCode 可能指向分类编码(不存在于主数据)而非科目父级
            BudgetCategory cat = categoryMapper.selectOne(new LambdaQueryWrapper<BudgetCategory>()
                .eq(BudgetCategory::getCategoryCode, bo.getParentCode())
                .last("LIMIT 1"));
            if (cat == null) {
                throw new ServiceException("上级科目编码[" + bo.getParentCode() + "]不存在");
            }
            bo.setCategoryCode(cat.getCategoryCode());
            bo.setLevel(2);
            // 分类下明细为纯主数据，不属于任何预算表
            bo.setTemplateId(null);
            bo.setTemplateCode(null);
            return;
        }
        int parentLevel = parent.getLevel() == null ? 1 : parent.getLevel();
        // 方案A：子科目硬继承父科目的模板归属，保证整棵树挂在同一张预算表下
        bo.setTemplateId(parent.getTemplateId());
        bo.setTemplateCode(StrUtil.blankToDefault(parent.getTemplateCode(), bo.getTemplateCode()));
        bo.setLevel(parentLevel + 1);
    }

    private LambdaQueryWrapper<BudgetSubjectMaster> buildWrapper(BudgetSubjectMasterBo bo) {
        LambdaQueryWrapper<BudgetSubjectMaster> qw = new LambdaQueryWrapper<>();
        if (bo != null) {
            qw.like(StrUtil.isNotBlank(bo.getSubjectCode()), BudgetSubjectMaster::getSubjectCode, bo.getSubjectCode());
            qw.like(StrUtil.isNotBlank(bo.getSubjectName()), BudgetSubjectMaster::getSubjectName, bo.getSubjectName());
            qw.eq(StrUtil.isNotBlank(bo.getSubjectType()), BudgetSubjectMaster::getSubjectType, bo.getSubjectType());
            qw.eq(StrUtil.isNotBlank(bo.getValidFlag()), BudgetSubjectMaster::getValidFlag, bo.getValidFlag());
            qw.like(StrUtil.isNotBlank(bo.getCategoryCode()), BudgetSubjectMaster::getCategoryCode, bo.getCategoryCode());
            qw.eq(StrUtil.isNotBlank(bo.getTemplateCode()), BudgetSubjectMaster::getTemplateCode, bo.getTemplateCode());
            qw.eq(ObjectUtil.isNotNull(bo.getBudgetYear()), BudgetSubjectMaster::getBudgetYear, bo.getBudgetYear());
            qw.eq(ObjectUtil.isNotNull(bo.getTemplateId()), BudgetSubjectMaster::getTemplateId, bo.getTemplateId());
            qw.eq(ObjectUtil.isNotNull(bo.getOrgId()), BudgetSubjectMaster::getOrgId, bo.getOrgId());
        }
        qw.orderByAsc(BudgetSubjectMaster::getLevel);
        qw.orderByAsc(BudgetSubjectMaster::getParentCode);
        qw.orderByAsc(BudgetSubjectMaster::getSort);
        qw.orderByAsc(BudgetSubjectMaster::getId);
        return qw;
    }

    @Override
    public Boolean changeValid(Long id, String validFlag) {
        if (id == null) {
            throw new ServiceException("科目ID不能为空");
        }
        String flag = StrUtil.blankToDefault(validFlag, "1");
        if (!"0".equals(flag) && !"1".equals(flag)) {
            throw new ServiceException("有效标记取值仅支持 0=停用 / 1=有效");
        }
        // 停用校验：存在有效子级时禁止停用，避免子级变孤儿
        if ("0".equals(flag)) {
            BudgetSubjectMaster subject = baseMapper.selectById(id);
            if (subject != null) {
                // 守卫：存在有效挂引用的科目禁止停用，先解除挂接后再停用
                long refs = countRefs(id);
                if (refs > 0) {
                    throw new ServiceException("科目[" + subject.getSubjectName() + "]已被 " + refs + " 个方案/预算表挂接，请先解除挂接后再停用");
                }
            }
            if (subject != null && StrUtil.isNotBlank(subject.getSubjectCode())) {
                Long activeChildren = baseMapper.selectCount(new LambdaQueryWrapper<BudgetSubjectMaster>()
                    .eq(BudgetSubjectMaster::getParentCode, subject.getSubjectCode())
                    .eq(StrUtil.isNotBlank(subject.getSubjectType()), BudgetSubjectMaster::getSubjectType, subject.getSubjectType())
                    .eq(ObjectUtil.isNotNull(subject.getOrgId()), BudgetSubjectMaster::getOrgId, subject.getOrgId())
                    .eq(BudgetSubjectMaster::getValidFlag, "1"));
                if (activeChildren != null && activeChildren > 0) {
                    throw new ServiceException("该科目下仍有 " + activeChildren + " 个有效子级科目，请先停用子级后再停用本科目");
                }
            }
        }
        return baseMapper.update(null, new LambdaUpdateWrapper<BudgetSubjectMaster>()
            .eq(BudgetSubjectMaster::getId, id)
            .set(BudgetSubjectMaster::getValidFlag, flag)) > 0;
    }

    @Override
    public Long countRefs(Long id) {
        if (id == null) {
            return 0L;
        }
        return templateItemMapper.selectCount(new LambdaQueryWrapper<BudgetTemplateItem>()
            .eq(BudgetTemplateItem::getRefSubjectId, id));
    }
}
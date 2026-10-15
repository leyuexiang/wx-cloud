package cn.iocoder.jtyf.module.dimensions.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.extra.pinyin.PinyinEngine;
import cn.hutool.extra.pinyin.PinyinUtil;
import cn.iocoder.jtyf.framework.common.exception.JtyfBusinessException;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceInfo;
import cn.iocoder.jtyf.module.dimensions.entity.ResourceType;
import cn.iocoder.jtyf.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.jtyf.module.dimensions.mapper.ResourceTypeMapper;
import cn.iocoder.jtyf.module.dimensions.service.ResourceInfoService;
import cn.iocoder.jtyf.module.dimensions.service.ResourceTypeService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;


@Service
public class ResourceTypeServiceImpl extends ServiceImpl<ResourceTypeMapper, ResourceType> implements ResourceTypeService {

    @jakarta.annotation.Resource
    ResourceInfoService resourceInfoService;


    @Override
    public String deleteType(Integer id) {
        if (id != null) {
            //先刪除这个分类
            removeById(id);
            //将这个分类下的资源的typeId置null
            List<ResourceInfo> resourceInfoList = resourceInfoService.list(new LambdaQueryWrapper<ResourceInfo>()
                    .eq(ResourceInfo::getTypeId, id));
            for (ResourceInfo resourceInfo : resourceInfoList) {
                resourceInfo.setTypeId(null);
                resourceInfoService.updateById(resourceInfo);
            }
            return "已删除对应资源且已重置typeId";
        }
        throw JtyfBusinessException.of(ErrorCodeConstants.TABLE_ID_IS_NULL);
    }

    @Override
    public String createType(ResourceType resourceType) {
        if (Objects.nonNull(resourceType.getId())) {
            return "id不用传";
        }
        // 校验 typeName 是否重复
        boolean typeNameExists = this.checkTypeNameExists(resourceType.getTypeName());
        if (typeNameExists) {
            throw JtyfBusinessException.of(ErrorCodeConstants.TYPE_NAME_EXIST ,resourceType.getTypeName());
        }

        // 生成 typeCode
        String typeCode = generateTypeCodeIsMD5(resourceType.getTypeName());
        // 校验 typeCode 是否重复
        boolean typeCodeExists = this.checkTypeCodeExists(typeCode);
        if (typeCodeExists) {
            throw JtyfBusinessException.of(ErrorCodeConstants.TYPE_CODE_EXIST,resourceType.getTypeCode());
        }
        resourceType.setTypeCode(typeCode);

        // 保存数据
        this.save(resourceType);
        return String.valueOf(resourceType.getId());
    }

    @Override
    public Integer updateType(ResourceType resourceType) {
        // 判断记录是否存在
        Integer id = resourceType.getId();
        if (null == id || null == this.getById(id)) {
            throw JtyfBusinessException.of(ErrorCodeConstants.DATA_NOT_EXIST);
        }

        // 获取原始记录
        ResourceType original = this.getById(id);

        // 如果名称发生了变化，才需要校验名称唯一性
        if (!Objects.equals(resourceType.getTypeName(), original.getTypeName())) {
            // 校验 typeName 是否重复（排除当前记录）
            boolean typeNameExists = this.checkTypeNameExistsExcludeSelf(resourceType.getTypeName(), id);
            if (typeNameExists) {
                throw JtyfBusinessException.of(ErrorCodeConstants.TYPE_NAME_EXIST ,resourceType.getTypeName());
            }

            // 生成 typeCode
            String typeCode = generateTypeCodeIsMD5(resourceType.getTypeName());
            // 校验 typeCode 是否重复（排除当前记录）
            boolean typeCodeExists = this.checkTypeCodeExistsExcludeSelf(typeCode, id);
            if (typeCodeExists) {
                throw JtyfBusinessException.of(ErrorCodeConstants.TYPE_CODE_EXIST,resourceType.getTypeCode());
            }
            resourceType.setTypeCode(typeCode);
        }

        // 使用更新方法而非保存方法
        boolean success = this.updateById(resourceType);
        if (success) {
            return id;
        }
        throw JtyfBusinessException.of(ErrorCodeConstants.UPDATE_FAIL);
    }

    /**
     * 使用 Hutool 的 DigestUtil 生成 MD5 哈希值作为 typeCode
     */
    private String generateTypeCodeIsMD5(String typeName) {
        // 生成32位大写MD5（Hutool的md5Hex默认返回32位小写，转大写处理）
        return StrUtil.isBlank(typeName) ? "" : DigestUtil.md5Hex(typeName).toUpperCase();
    }

    /**
     * 使用 Hutool 的 PinyinEngine 生成拼音首字母缩写作为 typeCode
     */
    private String generateTypeCode(String typeName) {
        if (StrUtil.isBlank(typeName)) {
            return "";
        }

        PinyinEngine engine = PinyinUtil.getEngine();
        StringBuilder sb = new StringBuilder();

        for (char c : typeName.toCharArray()) {
            // 使用 Unicode 范围判断中文字符
            if (isChineseChar(c)) {
                // 获取拼音首字母
                String pinyin = engine.getPinyin(c);
                if (StrUtil.isNotBlank(pinyin)) {
                    sb.append(pinyin.charAt(0));
                }
            } else {
                // 非汉字直接拼接
                sb.append(c);
            }
        }

        return sb.toString().toUpperCase();
    }

    /**
     * 判断字符是否为中文字符（包括简体和繁体）
     */
    private boolean isChineseChar(char c) {
        // CJK 统一表意文字范围：0x4E00-0x9FFF
        return c >= 0x4E00 && c <= 0x9FFF;
    }

    @Override
    public boolean checkTypeNameExists(String typeName) {
        return lambdaQuery().eq(ResourceType::getTypeName, typeName).count() > 0;
    }

    @Override
    public boolean checkTypeCodeExists(String typeCode) {
        return lambdaQuery().eq(ResourceType::getTypeCode, typeCode).count() > 0;
    }


    // 需要在Service中新增这两个方法用于排除自身的唯一性校验
    public boolean checkTypeNameExistsExcludeSelf(String typeName, Integer id) {
        return lambdaQuery()
                .eq(ResourceType::getTypeName, typeName)
                .ne(ResourceType::getId, id)
                .exists();
    }

    public boolean checkTypeCodeExistsExcludeSelf(String typeCode, Integer id) {
        return lambdaQuery()
                .eq(ResourceType::getTypeCode, typeCode)
                .ne(ResourceType::getId, id)
                .exists();
    }
}


package cn.iocoder.wx.module.dimensions.service.impl;

import cn.iocoder.wx.framework.common.exception.WxBusinessException;
import cn.iocoder.wx.framework.common.pojo.CommonResult;
import cn.iocoder.wx.module.dimensions.domain.vo.LabelVo;
import cn.iocoder.wx.module.dimensions.entity.*;
import cn.iocoder.wx.module.dimensions.exception.ErrorCodeConstants;
import cn.iocoder.wx.module.dimensions.mapper.AssociationObjectMapper;
import cn.iocoder.wx.module.dimensions.mapper.DeviceCommonMapper;
import cn.iocoder.wx.module.dimensions.service.*;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.text.DecimalFormat;
import java.util.*;


@Service
@DS("data")
public class AssociationObjectServiceImpl extends ServiceImpl<AssociationObjectMapper, AssociationObject> implements AssociationObjectService {

    @Resource
    AlertComponentInfoCbdsService alertComponentInfoCbdsService;

    @Resource
    AlertComponentInfoPpfmterService alertComponentInfoPpfmterService;

    @Resource
    AlertComponentStatusCbdsService alertComponentStatusCbdsService;

    @Resource
    AlertComponentStatusPpfmterService alertComponentStatusPpfmterService;

    @Resource
    DeviceCommonService deviceCommonService;

    @Resource
    DeviceCommonMapper deviceCommonMapper;

    @Override
    public List<AssociationObject> getLabel(String tableName) {

        List<DeviceCommon> deviceCommonList = deviceCommonService.selectByTableName(tableName);

        //依次先判断楼栋,楼层,空间是否是一样的
        Boolean differentBuilding = isBuildingValuesDifferent(deviceCommonList);
        Boolean differentFloor = isFloorValuesDifferent(deviceCommonList);

        AssociationObject rootNode = new AssociationObject();
        if(!differentBuilding){//同一楼栋，不需要构建楼栋

            if(!differentFloor){//同一楼栋且同一层，不需要构建楼栋-层,只剩空间
                //构建空间1层
               // 构建根节点
                //构建树形结构
               for (DeviceCommon deviceCommon : deviceCommonList) {
                   String spaceName = deviceCommon.getZonesubname();
                   buildOne(rootNode,spaceName);
               }
            }else {
               //同一楼栋且不同层，构建楼层-空间(2层)
               //构建根节点
                //构建树形结构
               for (DeviceCommon deviceCommon : deviceCommonList) {
                   String floor = deviceCommon.getFloorName();
                   String spaceName = deviceCommon.getZonesubname();
                   buildTwo(rootNode,floor,spaceName);
               }
            }

        }else{
            //楼栋都不一样，构建楼栋-层-空间(3层)
            //构建根节点
            //构建树形结构
            for (DeviceCommon deviceCommon : deviceCommonList) {
                String building = deviceCommon.getBuildingName();
                String floor = deviceCommon.getFloorName();
                String spaceName = deviceCommon.getZonesubname();
                buildThree(rootNode,building,floor,spaceName);
            }
        }
        return rootNode.getChildren();
    }

    private Boolean isBuildingValuesDifferent(List<DeviceCommon> deviceCommonList) {
        if (deviceCommonList == null || deviceCommonList.isEmpty()) {
            return false; // 空列表或只有一个元素时，字段值都是相同的
        }

        DeviceCommon deviceCommon = deviceCommonList.get(0);// 取第一个实体对象
        String buildingFirstValue = deviceCommon.getBuildingName();// 获取字段值作为基准

        for (int i = 1; i < deviceCommonList.size(); i++) {
            DeviceCommon deviceCommonAfter = deviceCommonList.get(i);
            String buildingAfterValue = deviceCommonAfter.getBuildingName(); // 获取当前实体对象的字段值
            if (!buildingFirstValue.equals(buildingAfterValue)) {
                return true; // 发现字段值不同，直接返回结果
            }
        }
        return false; // 所有字段值都相同
    }

    private Boolean isFloorValuesDifferent(List<DeviceCommon> deviceCommonList) {
        if (deviceCommonList == null || deviceCommonList.isEmpty()) {
            return false; // 空列表或只有一个元素时，字段值都是相同的
        }

        DeviceCommon deviceCommon = deviceCommonList.get(0);// 取第一个实体对象
        String floorFirstValue = deviceCommon.getFloorName();// 获取字段值作为基准

        for (int i = 1; i < deviceCommonList.size(); i++) {
            DeviceCommon deviceCommonAfter = deviceCommonList.get(i);
            String floorAfterValue = deviceCommonAfter.getFloorName(); // 获取当前实体对象的字段值
            if (!floorFirstValue.equals(floorAfterValue)) {
                return true; // 发现字段值不同，直接返回结果
            }
        }
        return false; // 所有字段值都相同
    }


    private void buildThree(AssociationObject rootNode, String building, String floor, String spaceName) {
        //从左到右进行层级分类
        AssociationObject currentNode = rootNode;
        currentNode =  findCreateNode(currentNode.getChildren(),building,building);
        currentNode =  findCreateNode(currentNode.getChildren(),floor,floor);
        findCreateNode(currentNode.getChildren(),spaceName,spaceName).setLeaf(true);
    }
    private void buildTwo(AssociationObject rootNode, String floor, String spaceName) {
        //从左到右进行层级分类
        AssociationObject currentNode = rootNode;
        currentNode =  findCreateNode(currentNode.getChildren(),floor,floor);
        findCreateNode(currentNode.getChildren(),spaceName,spaceName).setLeaf(true);
    }

    private void buildOne(AssociationObject rootNode, String spaceName) {
        //从左到右进行层级分类
        findCreateNode(rootNode.getChildren(),spaceName,spaceName).setLeaf(true);
    }

    @Override
    public List<String> getDeviceId(String[][] label) {
        List<String> deviceIdList = new ArrayList<>();
        for (String[] subLabel : label) {
            //四层查询：areaId-buildingId-floorId-typeId
            int level = subLabel.length;

            if (level == 1) {
                List<DeviceInfoCbds> deviceInfoCbdsList = alertComponentInfoCbdsService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoCbds>()
                        .eq(DeviceInfoCbds::getAreaId, subLabel[0]));

                List<DeviceInfoPpfmter> deviceInfoPpfmtersList = alertComponentInfoPpfmterService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoPpfmter>()
                        .eq(DeviceInfoPpfmter::getAreaId, subLabel[0]));

                addDeviceId(deviceIdList, deviceInfoCbdsList, deviceInfoPpfmtersList);

            } else if (level == 2) {
                List<DeviceInfoCbds> deviceInfoCbdsList = alertComponentInfoCbdsService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoCbds>()
                        .eq(DeviceInfoCbds::getAreaId, subLabel[0])
                        .eq(DeviceInfoCbds::getBuildingId, subLabel[1]));

                List<DeviceInfoPpfmter> deviceInfoPpfmtersList = alertComponentInfoPpfmterService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoPpfmter>()
                        .eq(DeviceInfoPpfmter::getAreaId, subLabel[0])
                        .eq(DeviceInfoPpfmter::getBuildingId, subLabel[1]));

                addDeviceId(deviceIdList, deviceInfoCbdsList, deviceInfoPpfmtersList);

            } else if (level == 3) {
                List<DeviceInfoCbds> deviceInfoCbdsList = alertComponentInfoCbdsService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoCbds>()
                        .eq(DeviceInfoCbds::getAreaId, subLabel[0])
                        .eq(DeviceInfoCbds::getBuildingId, subLabel[1])
                        .eq(DeviceInfoCbds::getFloorId, subLabel[2]));

                List<DeviceInfoPpfmter> deviceInfoPpfmtersList = alertComponentInfoPpfmterService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoPpfmter>()
                        .eq(DeviceInfoPpfmter::getAreaId, subLabel[0])
                        .eq(DeviceInfoPpfmter::getBuildingId, subLabel[1])
                        .eq(DeviceInfoPpfmter::getFloorId, subLabel[2]));

                addDeviceId(deviceIdList, deviceInfoCbdsList, deviceInfoPpfmtersList);

            } else if (level == 4) {
                List<DeviceInfoCbds> deviceInfoCbdsList = alertComponentInfoCbdsService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoCbds>()
                        .eq(DeviceInfoCbds::getAreaId, subLabel[0])
                        .eq(DeviceInfoCbds::getBuildingId, subLabel[1])
                        .eq(DeviceInfoCbds::getFloorId, subLabel[2])
                        .eq(DeviceInfoCbds::getType, subLabel[3]));

                List<DeviceInfoPpfmter> deviceInfoPpfmtersList = alertComponentInfoPpfmterService.getBaseMapper().selectList(new LambdaQueryWrapper<DeviceInfoPpfmter>()
                        .eq(DeviceInfoPpfmter::getAreaId, subLabel[0])
                        .eq(DeviceInfoPpfmter::getBuildingId, subLabel[1])
                        .eq(DeviceInfoPpfmter::getFloorId, subLabel[2])
                        .eq(DeviceInfoPpfmter::getType, subLabel[3]));

                addDeviceId(deviceIdList, deviceInfoCbdsList, deviceInfoPpfmtersList);

            }
        }
        return deviceIdList;
    }

    private static void addDeviceId(List<String> deviceIdList, List<DeviceInfoCbds> deviceInfoCbdsList, List<DeviceInfoPpfmter> deviceInfoPpfmtersList) {
        if(!deviceInfoCbdsList.isEmpty()){
            for (DeviceInfoCbds deviceInfoCbds : deviceInfoCbdsList) {
                deviceIdList.add(deviceInfoCbds.getDeviceId());
            }
        }
        if (!deviceInfoPpfmtersList.isEmpty()) {
            for (DeviceInfoPpfmter deviceInfoPpfmter : deviceInfoPpfmtersList) {
                deviceIdList.add(deviceInfoPpfmter.getDeviceId());
            }
        }
    }

    @Override
    public CommonResult<?> getDeviceInfos(String type) {
        if (Objects.equals(type, "CBDSS")) {
            List<DeviceInfoCbds> deviceInfoCbds = alertComponentInfoCbdsService.list(new LambdaQueryWrapper<>());
            return CommonResult.success(deviceInfoCbds);
        } else if (Objects.equals(type, "PPFMTER")) {
            List<DeviceInfoPpfmter> deviceInfoPpfmters = alertComponentInfoPpfmterService.list(new LambdaQueryWrapper<>());
            return CommonResult.success(deviceInfoPpfmters);
        } else {
            throw WxBusinessException.of(ErrorCodeConstants.DEVICE_PARAM_IS_ILLEGAL);
        }
    }

    @Override
    public CommonResult<?> getDeviceStatus(String type) {
        if (Objects.equals(type, "CBDSS")) {
            List<DeviceStatusCbds> deviceStatusCbdsList = alertComponentStatusCbdsService.list(new LambdaQueryWrapper<>());
            for (DeviceStatusCbds deviceStatusCbds : deviceStatusCbdsList) {
                /*
                 *  #.# 表示保留一位有效小数；
                    #.## 表示保留两位有效小数；
                    #.### 表示保留三位有效小数。
                 */
                DecimalFormat humidityFormat = new DecimalFormat("#.#");
                DecimalFormat COFormat = new DecimalFormat("#.##");
                DecimalFormat CO2Format = new DecimalFormat("#.###");
                deviceStatusCbds.setTemperature(getRandomInteger(15,35));
                deviceStatusCbds.setHumidity(Double.valueOf(humidityFormat.format(getRandomDouble(1))));
                deviceStatusCbds.setCo(Double.valueOf(COFormat.format(getRandomDouble(0.1))));
                deviceStatusCbds.setCo2(Double.valueOf(CO2Format.format(getRandomDouble(0.05))));
            }
            List<DeviceStatusCbds> randomDeviceStatusCbdsList = getRandomData(deviceStatusCbdsList);
            return CommonResult.success(randomDeviceStatusCbdsList);
        } else if (Objects.equals(type, "PPFMTER")) {
            List<DeviceStatusPpfmter> deviceStatusPpfmterList = alertComponentStatusPpfmterService.list(new LambdaQueryWrapper<>());
            for (DeviceStatusPpfmter deviceStatusPpfmter : deviceStatusPpfmterList) {
                deviceStatusPpfmter.setParticipants(getRandomInteger(0,1000));
                deviceStatusPpfmter.setDepartures(getRandomInteger(0,1000));
                deviceStatusPpfmter.setHeadCount(getRandomInteger(0,1000));
            }
           List<DeviceStatusPpfmter> randomDeviceStatusPpfmterList = getRandomData1(deviceStatusPpfmterList);
            return CommonResult.success(randomDeviceStatusPpfmterList);
        } else {
            return CommonResult.error(500,"请传正确的设设备类型参数！");
        }
    }

    private List<DeviceStatusPpfmter> getRandomData1(List<DeviceStatusPpfmter> deviceStatusPpfmterList) {
        Random random = new Random();
        int randomNumber = random.nextInt(100); // 生成 0 到 99 之间的随机数

        if (randomNumber < 50) {
            // 50% 概率返回 30 条数据
            return getRandomSubset1(deviceStatusPpfmterList, 30);
        } else if ( randomNumber < 75) {
            // 25% 概率返回 25 到 29 条数据
            int randomSize = random.nextInt(5) + 25; // 生成 25 到 29 之间的随机数
            return getRandomSubset1(deviceStatusPpfmterList, randomSize);
        } else {
            // 25% 概率返回 31 到 35 条数据
            int randomSize = random.nextInt(5) + 31; // 生成 31 到 35 之间的随机数
            return getRandomSubset1(deviceStatusPpfmterList, randomSize);
        }
    }

    private List<DeviceStatusPpfmter> getRandomSubset1(List<DeviceStatusPpfmter> deviceStatusPpfmterList, int size) {
        Random random = new Random();
        List<DeviceStatusPpfmter> subset = new ArrayList<>();
        while (subset.size() < size) {
            int randomIndex = random.nextInt(deviceStatusPpfmterList.size()); // 生成 0 到 (data.size() - 1) 之间的随机数
            DeviceStatusPpfmter element = deviceStatusPpfmterList.get(randomIndex);
            if (!subset.contains(element)) {
                subset.add(element);
            }
        }
        return subset;
    }

    private List<DeviceStatusCbds> getRandomData(List<DeviceStatusCbds> deviceStatusCbdsList) {
        Random random = new Random();
        int randomNumber = random.nextInt(100); // 生成 0 到 99 之间的随机数

        if (randomNumber < 50) {
            // 50% 概率返回 30 条数据
            return getRandomSubset(deviceStatusCbdsList, 30);
        } else if ( randomNumber < 75) {
            // 25% 概率返回 25 到 29 条数据
            int randomSize = random.nextInt(5) + 25; // 生成 25 到 29 之间的随机数
            return getRandomSubset(deviceStatusCbdsList, randomSize);
        } else {
            // 25% 概率返回 31 到 35 条数据
            int randomSize = random.nextInt(5) + 31; // 生成 31 到 35 之间的随机数
            return getRandomSubset(deviceStatusCbdsList, randomSize);
        }
    }

    private List<DeviceStatusCbds> getRandomSubset(List<DeviceStatusCbds> deviceStatusCbdsList, int size) {
        Random random = new Random();
        List<DeviceStatusCbds> subset = new ArrayList<>();
        while (subset.size() < size) {
            int randomIndex = random.nextInt(deviceStatusCbdsList.size()); // 生成 0 到 (data.size() - 1) 之间的随机数
            DeviceStatusCbds element = deviceStatusCbdsList.get(randomIndex);
            if (!subset.contains(element)) {
                subset.add(element);
            }
        }
        return subset;
    }



    private Double getRandomDouble(double max) {
        Random random = new Random();
        return 0 + random.nextDouble()*(max- 0);
    }

    private Integer getRandomInteger(int min, int max) {
        Random random = new Random();
        OptionalInt first = random.ints(min, max + 1).findFirst();
        if(first.isPresent()){
            return first.getAsInt();
        }
        return 0;
    }

    @Override
    public List<AssociationObject> getNewLabel(String typeOrSpace) {
        if(Objects.equals(typeOrSpace, "type")){
            List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectDeviceAllList();
            AssociationObject rootNode = new AssociationObject();
            for (DeviceCommon deviceCommon : deviceCommonList) {
                String deviceTypeName = deviceCommon.getDeviceTypeName();
                String deviceTypeCode = deviceCommon.getDeviceTypeCode();
                buildTypeTwo(rootNode,deviceTypeName,deviceTypeCode);
            }
            return rootNode.getChildren();
        }else if (Objects.equals(typeOrSpace, "space")) {
            List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectDeviceAllList();
            AssociationObject rootNode = new AssociationObject();
            //构建园区area-楼栋building-楼层floor-空间spaceName-设备类型编码deviceTypeCode(5层)
            //构建根节点,构建树形结构
            for (DeviceCommon deviceCommon : deviceCommonList) {
                String area = deviceCommon.getAreaName();
                String building = deviceCommon.getBuildingName();
                String floor = deviceCommon.getFloorName();
                String spaceName = deviceCommon.getZonesubname();
                String deviceTypeCode = deviceCommon.getDeviceTypeCode();
                String deviceTypeName = deviceCommon.getDeviceTypeName();
                buildFive(rootNode,area,building,floor,spaceName,deviceTypeCode,deviceTypeName);
            }
            return rootNode.getChildren();
        }
        throw WxBusinessException.of(ErrorCodeConstants.PARAM_IS_ILLEGAL);
    }

    private void buildTypeTwo(AssociationObject rootNode, String deviceTypeName, String deviceTypeCode) {
        //从左到右进行层级分类
        AssociationObject currentNode = rootNode;
        currentNode =  findCreateNode(currentNode.getChildren(),deviceTypeName,deviceTypeName);
        findCreateNode(currentNode.getChildren(),deviceTypeCode,deviceTypeCode).setLeaf(true);
    }

    private void buildFive(AssociationObject rootNode, String area, String building, String floor, String spaceName, String deviceTypeCode, String deviceTypeName) {
        //从左到右进行层级分类
        AssociationObject currentNode = rootNode;
        currentNode =  findCreateNode(currentNode.getChildren(),area,area);
        currentNode =  findCreateNode(currentNode.getChildren(),building,building);
        currentNode =  findCreateNode(currentNode.getChildren(),floor,floor);
        currentNode =  findCreateNode(currentNode.getChildren(),spaceName,spaceName);
        findCreateLeafNode(currentNode.getChildren(),deviceTypeCode,deviceTypeCode,deviceTypeName).setLeaf(true);
    }
    //判断孩儿们是否有传入的这个value，没有就创建，有就直接返回，不用创建;(孩儿=种类)
    private AssociationObject findCreateNode(List<AssociationObject> children, String value, String label) {
        for (AssociationObject child : children) {
            if(child.getValueName().equals(value)){
                return child;
            }
        }
        AssociationObject newNode = new AssociationObject();
        newNode.setValueName(value);
        newNode.setLabelName(label);
        children.add(newNode);
        return newNode;
    }
    private AssociationObject findCreateLeafNode(List<AssociationObject> children, String value, String label, String BeforeLabel) {
        for (AssociationObject child : children) {
            if(child.getValueName().equals(value)){
                return child;
            }
        }
        AssociationObject newNode = new AssociationObject();
        newNode.setValueName(value);
        newNode.setLabelName(BeforeLabel + label);
        children.add(newNode);
        return newNode;
    }

    @Override
    public List<String> getDeviceIdByValue(LabelVo labelVo) {
        List<String> deviceIdList = new ArrayList<>();
        String typeOrSpace = labelVo.getTypeOrSpace();
        String[][] value = labelVo.getValue();
        if (Objects.equals(typeOrSpace, "space")) {
            for (String[] subValue : value) {
                int level = subValue.length;
                if (level == 1) {
                    List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectWhereArea(subValue[0]);

                    addDeviceIds(deviceIdList, deviceCommonList);

                } else if (level == 2) {
                    List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectWhereBuilding(subValue[0], subValue[1]);

                    addDeviceIds(deviceIdList, deviceCommonList);

                } else if (level == 3) {
                    List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectWhereFloor(subValue[0], subValue[1], subValue[2]);

                    addDeviceIds(deviceIdList, deviceCommonList);

                } else if (level == 4) {
                    List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectWhereSpaceName(subValue[0], subValue[1], subValue[2], subValue[3]);

                    addDeviceIds(deviceIdList, deviceCommonList);
                } else if (level == 5) {
                    List<DeviceCommon> deviceCommonList = deviceCommonMapper.selectWhereDeviceTypeCode(subValue[0], subValue[1], subValue[2], subValue[3], subValue[4]);

                    addDeviceIds(deviceIdList, deviceCommonList);
                }
            }
            return deviceIdList;
        } else if (Objects.equals(typeOrSpace, "type")) {
            List<String> deviceCodeList = new ArrayList<>();
            for (String[] subValue : value) {
                int level = subValue.length;
                if (level == 1) {
                    deviceCodeList.addAll(deviceCommonMapper.selectDeviceCode(subValue[0], null)) ;
                } else if (level == 2) {
                    deviceCodeList.addAll(deviceCommonMapper.selectDeviceCode(subValue[0], subValue[1]));
                }else {
                    throw WxBusinessException.of(ErrorCodeConstants.PARAM_NUM_IN_ARRAY_INCORRECT);
                }
            }
            return deviceCodeList;
        }
        throw WxBusinessException.of(ErrorCodeConstants.ONLY_TYPE_AND_SPACE_CAN_PASSED);
    }

    private static void addDeviceIds(List<String> deviceIdList, List<DeviceCommon> deviceCommonList) {
        if(!deviceCommonList.isEmpty()){
            for (DeviceCommon deviceCommons : deviceCommonList) {
                deviceIdList.add(deviceCommons.getDeviceId());
            }
        }
    }

}

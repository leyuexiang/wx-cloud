package cn.iocoder.wx.module.dimensions.utils;

import java.lang.reflect.Field;

public class EntityUtils {

    public static  void  copyProperties(Object source ,Object target) throws IllegalAccessException {
        Class<?> sourceClass = source.getClass();
        Class<?> targetClass = target.getClass();

        Field[] sourceClassDeclaredFields = sourceClass.getDeclaredFields();
        Field[] targetClassDeclaredFields = targetClass.getDeclaredFields();

        for (Field sourceClassDeclaredField : sourceClassDeclaredFields) {
            sourceClassDeclaredField.setAccessible(true);
            Object sourceValue = sourceClassDeclaredField.get(source);

            for (Field targetClassDeclaredField : targetClassDeclaredFields) {
                targetClassDeclaredField.setAccessible(true);

                if(targetClassDeclaredField.getName().equals(sourceClassDeclaredField.getName())){
                    targetClassDeclaredField.set(target,sourceValue);
                    break;
                }
            }
        }
    }
}

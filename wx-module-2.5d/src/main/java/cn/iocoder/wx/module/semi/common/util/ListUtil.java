package cn.iocoder.wx.module.semi.common.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ListUtil {

    public static boolean isEmptyList(List<?> lst) {
        if (lst == null || lst.size() == 0) {
            return true;
        }
        return false;
    }

    public static boolean isNotEmptyList(List<?> lst) {
        if (lst != null && lst.size() > 0) {
            return true;
        }
        return false;
    }

    public static boolean containOne(List<?> srcLst, List<?> cmpLst) {
        if (srcLst == null || cmpLst == null){
            return false;
        }
        for (Object o : srcLst) {
            if (cmpLst.contains(o)) {
                return true;
            }
        }
        return false;
    }

    public static boolean equalLists(List<String> one, List<String> two){
        if (one == null || two == null){
            return false;
        }
        if (one.size() != two.size()){
            return false;
        }
        // use a copy to avoid messing the order of the lists
        one = new ArrayList<String>(one);
        two = new ArrayList<String>(two);
        Collections.sort(one);
        Collections.sort(two);
        return one.equals(two);
    }

    public static <T> List<T> asNewList(T[] arr) {
        if (arr == null) return null;
        List<T> lst = new ArrayList<T>();
        for (T ele : arr) {
            lst.add(ele);
        }
        return lst;
    }
}

package cn.iocoder.wx.module.semi.common.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class StrUtil {

    public static boolean isEmpty(String str) {
        if (str == null || "".equals(str.trim())) {
            return true;
        }
        return false;
    }

    public static boolean isNotEmpty(String str) {
        return !isEmpty(str);
    }

    public static boolean isNotEmpty(String... strs) {
        for (String str : strs) {
            if (isEmpty(str)) {
                return false;
            }
        }
        return true;
    }

    public static String safeToStr(Object o) {
        if (o == null) {
            return "";
        }
        return o.toString();
    }

    public static Integer safeToInt(String str) {
        Integer i = null;
        if (StrUtil.isNotEmpty(str)) {
            try {
                i = Integer.valueOf(str);
            } catch (Exception e) {
            }
        }
        return i;
    }

    public static List<Integer> safeToIntLst(List<String> strLst) {
        List<Integer> intLst = new ArrayList<Integer>();
        if (strLst != null) {
            for (String str : strLst) {
                intLst.add(safeToInt(str));
            }
        }
        return intLst;
    }

    public static Long safeToLong(String str) {
        Long l = null;
        try {
            l = Long.parseLong(str);
        } catch (NumberFormatException e) {
        }
        return l;
    }

    public static String[] splitAll(String str, String delimiter) {
        return str.split(delimiter, -1);
    }

    public static String strLstAgg(List<String> lst, String delimiter) {
        if (ListUtil.isEmptyList(lst)) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lst.size(); i++) {
            String l = lst.get(i);
            sb.append((i == 0) ? l : delimiter + l);
        }
        return sb.toString();
    }

    public static String strArrAgg(String[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            String str = safeToStr(array[i]);
            if (i == 0) {
                sb.append(str);
            } else {
                sb.append(delimiter).append(str);
            }
        }
        return sb.toString();
    }

    public static String strArrAggWithoutEmpty(String[] array, String delimiter) {
        if (array == null || array.length == 0) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < array.length; i++) {
            if (isNotEmpty(array[i])) {
                sb.append(delimiter).append(array[i]);
            }
        }

        return sb.length() > 0 ? sb.substring(delimiter.length()) : "";
    }

    public static String join(Collection<String> coll, String sep, String quote) {
        if (coll == null || coll.isEmpty()) {
            return "";
        }

        StringBuilder buf = new StringBuilder();
        for (String str : coll) {
            buf.append(sep).append(quote).append(str).append(quote);
        }

        return buf.substring(sep.length());
    }

    public static String join(Collection<String> coll, String sep, String quote, String append) {
        if (coll == null || coll.isEmpty()) {
            return "";
        }

        StringBuilder buf = new StringBuilder();
        for (String str : coll) {
            buf.append(sep).append(quote).append(str).append(append).append(quote);
        }

        return buf.substring(sep.length());
    }

    public static String emptyAsNull(String value) {
        if ("".equals(value)) {
            return null;
        }
        return value;
    }

    public static int compareNullLast(String s1, String s2) {
        if (isEmpty(s1)) {
            if (isEmpty(s2)) {
                return 0;
            } else {
                return 1;
            }
        } else {
            if (isEmpty(s2)) {
                return -1;
            } else {
                return s1.compareTo(s2);
            }
        }
    }

    public static boolean equals(String s1, String s2) {
        if (isEmpty(s1)) {
            return isEmpty(s2);
        } else {
            if (isEmpty(s2)) {
                return false;
            } else {
                return s1.equals(s2);
            }
        }
    }

    public static int getLength(String inStr) {
        int len = 0;
        for (int i = 0; i < inStr.length(); i++) {
            int codePoint = inStr.codePointAt(i);
            if (codePoint >= 0 && codePoint <= 127) {
                len += 1;
            } else {
                len += 2;
            }
        }
        return len;
    }

    public static String leftPad(String orgin, int length, String place) {
        if (orgin == null) {
            orgin = "";
        }
        while (orgin.length() < length) {
            orgin = place + orgin;
        }
        return orgin;
    }

    public static <T> boolean isAllMatch(T[] items, T value) {
        for (T item : items) {
            if (!value.equals(item)) {
                return false;
            }
        }
        return true;
    }
}

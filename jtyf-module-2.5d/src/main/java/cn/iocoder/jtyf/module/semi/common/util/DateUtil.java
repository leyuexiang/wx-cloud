package cn.iocoder.jtyf.module.semi.common.util;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {

    public static final String DATE_FORMAT_YMD = "yyyy-MM-dd";

    public static final String DATE_FORMAT_YYYYMMDD = "yyyyMMdd";

    public static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    public static final String DATE_FORMAT_DW = "yyyyMMddHHmmss";

    public static final String DATE_FORMAT_DW_MS = "yyyyMMddHHmmssSSS";

    public static String toStrForYmd(Date d) {
        return toStr(d, DATE_FORMAT_YMD);
    }

    public static String toStrForYmd(Timestamp t) {
        return toStr(t, DATE_FORMAT_YMD);
    }

    public static String toStrForYYYYMMDD(Date d) {
        return toStr(d, DATE_FORMAT_YYYYMMDD);
    }

    public static String toStr(Date d) {
        return toStr(d, DATE_FORMAT);
    }

    public static String toStr(Timestamp t) {
        return toStr(t, DATE_FORMAT);
    }

    public static String toStrForDw(Date d) {
        return toStr(d, DATE_FORMAT_DW);
    }

    public static String toStrForDwMs(Date d) {
        return toStr(d, DATE_FORMAT_DW_MS);
    }

    private static String toStr(Date d, String format) {
        if (d == null) {
            return "";
        }
        String dateStr = new SimpleDateFormat(format).format(d);
        return dateStr;
    }

    /**
     * String to TIMESTAMP
     *
     * @param strTime
     *            format格式日期字符串
     * @param format
     *            yyyy-MM-dd <strong>OR</strong> yyyy-MM-dd HH:mm:ss
     * @return
     */
    public static Timestamp toTimestamp(String strTime, String format) {
        Calendar rightNow = Calendar.getInstance();
        if (DATE_FORMAT_YMD.equals(format)) {
            String[] ob = strTime.split("-");
            rightNow.set(
                    Integer.valueOf(ob[0]),
                    Integer.valueOf(ob[1])-1,
                    Integer.valueOf(ob[2]),
                    0,
                    0,
                    0);
        } else {
            String[] ob = strTime.split(" ");
            String[] obDate = ob[0].split("-");
            String[] obTime = ob[1].split(":");
            rightNow.set(
                    Integer.valueOf(obDate[0]),
                    Integer.valueOf(obDate[1])-1,
                    Integer.valueOf(obDate[2]),
                    Integer.valueOf(obTime[0]),
                    Integer.valueOf(obTime[1]),
                    Integer.valueOf(obTime[2]));
        }
        rightNow.set(Calendar.MILLISECOND, 0);
        return new Timestamp(rightNow.getTimeInMillis());
    }

    public static boolean validateDateString(String dateString, String format) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(format);
            Date date = (Date) dateFormat.parseObject(dateString);
            return dateString.equals(dateFormat.format(date));
        } catch (Exception e) {
            return false;
        }
    }

    public static String convertDateString(String dateString, String frFormat, String toFormat) {
        try {
            SimpleDateFormat dateFormat = new SimpleDateFormat(frFormat);
            Date date = (Date) dateFormat.parseObject(dateString);
            dateFormat.applyPattern(toFormat);
            return dateFormat.format(date);
        } catch (Exception e) {
            return null;
        }
    }
}

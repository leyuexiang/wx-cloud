package cn.iocoder.wx.module.semi.common.msg;


import cn.iocoder.wx.module.semi.common.GlobalConst;
import cn.iocoder.wx.module.semi.common.prop.CmnPropertiesUtils;

import java.io.IOException;
import java.io.InputStreamReader;
import java.text.MessageFormat;
import java.util.MissingResourceException;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;

public class MsgResourceManager {

    private static ResourceBundle messages;

    static {
        initResource();
    }

    private static void initResource() {
        String path = CmnPropertiesUtils.getMsgResFile();
        try {
            messages = new PropertyResourceBundle(
                    new InputStreamReader(
                            MsgResourceManager.class.getClassLoader().getResourceAsStream("msgResource.properties"),
                    GlobalConst.SYS_FILE_ENCODING));
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }

    public static String getMessage(String messageCode) {
        return getMessage(messageCode, null);
    }

    public static String getMessage(String messageCode, Object[] arguments) {
        String message = null;

        try {
            message = messages.getString(messageCode);
        } catch (MissingResourceException e) {
            return "";
        }

        if (arguments != null) {
            try {
                message = MessageFormat.format(message, arguments);
            } catch (IllegalArgumentException e) {
                return "";
            }
        }

        return message;
    }
}

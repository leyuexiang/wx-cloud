package cn.iocoder.wx.module.semi.common.conf;

import cn.iocoder.wx.module.semi.common.util.ApiConst;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class CorsListConfParser extends BaseConfParser {

    private static CorsListConfParser instance = new CorsListConfParser(ApiConst.API_CORS_LIST_FILE);

    private List<String> corsList = new ArrayList<String>();

    private final ReadWriteLock rwlock = new ReentrantReadWriteLock();

    private final Lock rLock = rwlock.readLock();

    private final Lock wLock = rwlock.writeLock();

    public static CorsListConfParser getInstance() {
        instance.init();
        return instance;
    }

    // --------------------
    // Override base
    // --------------------
    @Override
    protected void init() {
        wLock.lock();
        try {
            super.init();
        } finally {
            wLock.unlock();
        }
    }

    @Override
    protected void clearElements() {
        corsList = new ArrayList<String>();
    }

    @Override
    protected boolean checkLine(String lineStr, int lineNum) {
        return true;
    }

    @Override
    protected void parseLine(String lineStr, int lineNum) {
        corsList.add(lineStr);
    }

    private CorsListConfParser(String filePath) {
        super(filePath);
    }

    //------------------------------
    // get data list copy read lock
    //------------------------------
    public List<String> getCorsList() {
        rLock.lock();
        try {
            return corsList;
        } finally {
            rLock.unlock();
        }
    }
}

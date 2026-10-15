package cn.iocoder.jtyf.module.semi.common.conf;

public interface ParserStgy {
    public boolean check(String lineStr, int lineNum);
    public void parse(String lineStr, int lineNum);
}

package com.example.dell.tracktest;


import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Created by dell on 2017/6/4.
 */
//日期转换工具类
public class DataUtils {
    private static SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public static String toDate(Date date) {
        return sdf.format(date);
    }

    //    距离格式化工具
    public static String DistanceFormat(double sum) {
        String routeLength = "";
        routeLength = new java.text.DecimalFormat("#.00").format(sum);
        return routeLength;

    }
}

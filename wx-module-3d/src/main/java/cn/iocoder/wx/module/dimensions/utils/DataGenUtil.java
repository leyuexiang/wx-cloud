package cn.iocoder.wx.module.dimensions.utils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;


public class DataGenUtil {
    public static void main(String[] args)  {
        String url = "jdbc:mysql://172.27.17.108:30030/test_3d_resource";
        String username = "root";
        String password = "123456";

//        try{
//            Connection connection = DriverManager.getConnection(url, username, password);
//            List<Map<String, Object>> data = generateData();
//            insertData(connection,data);
//            connection.close();
//            System.out.println("数据插入完成");
//        }catch (SQLException e){
//            e.printStackTrace();
//        }
//
//        try{
//            Connection connection = DriverManager.getConnection(url, username, password);
//            List<Map<String, Object>> data = generateData1();
//            insertData1(connection,data);
//            connection.close();
//            System.out.println("数据插入完成");
//        }catch (SQLException e){
//            e.printStackTrace();
//        }
//
//        try{
//            Connection connection = DriverManager.getConnection(url, username, password);
//            List<Map<String, Object>> data = generateData2();
//            insertData2(connection,data);
//            connection.close();
//            System.out.println("数据插入完成");
//        }catch (SQLException e){
//            e.printStackTrace();
//        }
//        try{
//            Connection connection = DriverManager.getConnection(url, username, password);
//            List<Map<String, Object>> data = generateData3();
//            insertData3(connection,data);
//            connection.close();
//            System.out.println("数据插入完成");
//        }catch (SQLException e){
//            e.printStackTrace();
//        }

    }


    private static void insertData(Connection connection, List<Map<String, Object>> data) throws SQLException {

        String sql = "INSERT INTO test_data(id,userName,longitude,latitude,posType) values (?,?,?,?,?)";
        PreparedStatement statement = connection.prepareStatement(sql);

        for (Map<String,Object> record: data) {
            statement.setInt(1,(int) record.get("id"));
            statement.setString(2, (String) record.get("userName"));
            statement.setFloat(3, (Float) record.get("longitude"));
            statement.setFloat(4, (Float) record.get("latitude"));
            statement.setString(5, (String) record.get("posType"));
            statement.executeUpdate();
        }
        statement.close();
    }

    private static void insertData1(Connection connection, List<Map<String, Object>> data) throws SQLException {

        String sql = "update device_info_ppfmter set lon = (?) where id = (?) ";
        //String sql = "update device_info_ppfmter set lat = (?) where id = (?) ";
        PreparedStatement statement = connection.prepareStatement(sql);

        for (Map<String,Object> record: data) {
            statement.setInt(2, (Integer) record.get("id"));
            statement.setDouble(1, (Double) record.get("lon"));
            //statement.setDouble(1, (Double) record.get("lat"));
            statement.executeUpdate();
        }
        statement.close();
    }
    private static void insertData2(Connection connection, List<Map<String, Object>> data) throws SQLException {

        String sql = "update device_status_cbds set temperature = (?),humidity=(?),co=(?) ,co2=(?) where id = (?) ";
        //String sql = "update device_info_ppfmter set lat = (?) where id = (?) ";
        PreparedStatement statement = connection.prepareStatement(sql);

        for (Map<String,Object> record: data) {
            statement.setInt(1, (Integer) record.get("temperature"));
            statement.setDouble(2, (Double) record.get("humidity"));
            statement.setDouble(3, (Double) record.get("co"));
            statement.setDouble(4, (Double) record.get("co2"));
            statement.setInt(5, (Integer) record.get("id"));
            //statement.setDouble(1, (Double) record.get("lat"));
            statement.executeUpdate();
        }
        statement.close();
    }

    private static void insertData3(Connection connection, List<Map<String, Object>> data) throws SQLException {

        String sql = "update device_status_ppfmter set departures = (?),participants=(?),head_count=(?) ,state=(?) where id = (?) ";
        //String sql = "update device_info_ppfmter set lat = (?) where id = (?) ";
        PreparedStatement statement = connection.prepareStatement(sql);

        for (Map<String,Object> record: data) {
            statement.setInt(1, (Integer) record.get("departures"));
            statement.setInt(2, (Integer) record.get("participants"));
            statement.setInt(3, (Integer) record.get("headCount"));
            statement.setInt(4, (Integer) record.get("state"));
            statement.setInt(5, (Integer) record.get("id"));
            //statement.setDouble(1, (Double) record.get("lat"));
            statement.executeUpdate();
        }
        statement.close();
    }

    private static List<Map<String, Object>> generateData() {
        ArrayList<Map<String, Object>> data = new ArrayList<>();
        HashSet<Integer> idSet = new HashSet<>();
        HashSet<Float> longitudeSet = new HashSet<>();
        HashSet<Float> latitudeSet = new HashSet<>();
        Random random = new Random();
        String[] names ={"坤坤","明明","帅帅","兰兰","玉玉","毛毛","拉拉","蹦蹦","哈哈","暖暖"};

        for (int i = 0; i <= 100; i++) {
            int id = getRandomUniqueId(idSet);
            String userName = names[random.nextInt(names.length)];
            float longitude = getRandomUniqueFloat(longitudeSet,5000);
            float latitude = getRandomUniqueFloat(latitudeSet,5000);
            String posType = "4";

            HashMap<String, Object> record = new HashMap<>();
            record.put("id",id);
            record.put("userName",userName);
            record.put("longitude",longitude);
            record.put("latitude",latitude);
            record.put("posType",posType);

            data.add(record);
        }
        return data;
    }
    private static List<Map<String, Object>> generateData1() {
        List<Map<String, Object>> data1 = new ArrayList<>();
        HashSet<Double> lonSet = new HashSet<>();
        HashSet<Double> latSet = new HashSet<>();


        for (int i = 1; i <= 30; i++) {
            DecimalFormat doubleFormat = new DecimalFormat("#.##");
            Double lon = Double.valueOf(doubleFormat.format(getRandomDouble(lonSet,-1000,1000)));
            Double lat = Double.valueOf(doubleFormat.format(getRandomDouble(latSet,-1000,1000)));

            HashMap<String, Object> record = new HashMap<>();

            record.put("id", i);
            record.put("lon",lon);
            record.put("lat",lat);

            data1.add(record);
        }
        return data1;
    }
    private static List<Map<String, Object>> generateData2() {
        List<Map<String, Object>> data2 = new ArrayList<>();
        HashSet<Double> humiditySet = new HashSet<>();
        HashSet<Double> coSet = new HashSet<>();
        HashSet<Double> co2Set = new HashSet<>();

        for (int i = 1; i <= 35; i++) {
            DecimalFormat doubleFormat = new DecimalFormat("#.##");
            DecimalFormat doubleFormatThree = new DecimalFormat("#.###");
            Integer temperature = getRandomInteger(15,35);
            Double humidity = Double.valueOf(doubleFormat.format(getRandomDouble(humiditySet,0,1)));
            Double co = Double.valueOf(doubleFormat.format(getRandomDouble(coSet,0,0.1)));
            Double co2 = Double.valueOf(doubleFormatThree.format(getRandomDouble(co2Set,0,0.05)));

            HashMap<String, Object> record = new HashMap<>();
            record.put("id", i);
            record.put("temperature",temperature);
            record.put("humidity",humidity);
            record.put("co",co);
            record.put("co2",co2);
            data2.add(record);
        }
        return data2;

    }

    private static List<Map<String, Object>> generateData3() {
        List<Map<String, Object>> data3 = new ArrayList<>();

        for (int i = 1; i <= 35; i++) {
            Integer departures = getRandomInteger(0,1000);
            Integer participants = getRandomInteger(0,1000);
            Integer headCount   = getRandomInteger(0,1000);
            Integer state = getRandomInteger(0,2);


            HashMap<String, Object> record = new HashMap<>();
            record.put("id", i);
            record.put("departures",departures);
            record.put("participants",participants);
            record.put("headCount",headCount);
            record.put("state",state);
            data3.add(record);
        }
        return data3;

    }
    private static Integer getRandomInteger(int min, int max) {
        Random random = new Random();
        OptionalInt first = random.ints(min, max + 1).findFirst();
        if(first.isPresent()){
            return first.getAsInt();
        }
        return 0;
    }
    private static Double getRandomDouble(HashSet<Double> floatSet, int min, double max) {
        Random random = new Random();
        Double floatValue = min + random.nextDouble()*(max-min);
        while (floatSet.contains(floatValue)){
            floatValue = min + random.nextDouble()*(max-min);
        }
        floatSet.add(floatValue);
        return floatValue;
    }

    private static float getRandomUniqueFloat(Set<Float> floatSet, float limit) {
        Random random = new Random();
        float floatValue = random.nextFloat() * limit;
        while (floatSet.contains(floatValue)){
            floatValue = random.nextFloat()*limit;
        }
        floatSet.add(floatValue);
        return floatValue;
    }

    private static int getRandomUniqueId(HashSet<Integer> idSet) {
        Random random = new Random();
       int id =  random.nextInt(10000000) + 1;
       while (idSet.contains(id)){
           id = random.nextInt(10000000)+1;
       }
       idSet.add(id);
       return id;
    }

}

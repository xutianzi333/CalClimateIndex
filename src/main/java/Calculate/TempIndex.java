package Calculate;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import javax.sql.DataSource;
import java.io.*;
import java.sql.*;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

public class TempIndex {
    /**
     * 计算气温年景指数
     *
     * @param dataPath 数据文件路径
     * @return 返回样本数据气温年景指数与计算数据气温年景指数
     */
    public List<Double> calculate(String dataPath, int beginYear, int endYear, int calYear) {
        // 1、读文件获取按年分组数据
        ConcurrentHashMap<String, HashMap<String, List<Double>>> sampleDataMap = getDataMap(dataPath, beginYear, endYear);
        // 2、将数据按旬分组
        HashMap<String, HashMap<String, List<Double>>> formativeSampleDataMap = getFormativeDataMap(sampleDataMap);
        // 3、计算平均值与标准差
        HashMap<String, List<Double>> avgMap = getAvgMap(formativeSampleDataMap);
        HashMap<String, List<Double>> devMap = getDevMap(formativeSampleDataMap, avgMap);
        // 4、计算气温年景指数
        return calTempIndex(dataPath, formativeSampleDataMap, avgMap, devMap, beginYear, endYear, calYear);
    }

    /**
     * 计算样本年和计算年的气温年景指数
     *
     * @param dataPath               计算年数据文件路径
     * @param formativeSampleDataMap 格式化后的样本年数据集合
     * @param avgMap                 平均值集合
     * @param devMap                 标准差集合
     * @return 存有31年气温年景指数的集合
     */
    private List<Double> calTempIndex(String dataPath, HashMap<String, HashMap<String, List<Double>>> formativeSampleDataMap, HashMap<String, List<Double>> avgMap, HashMap<String, List<Double>> devMap, int beginYear, int endYear, int calYear) {
        DecimalFormat df = new DecimalFormat("#.00");//设置保留位数
        List<Double> tempIndexList = new ArrayList<>();

        // 1、获取计算年（例：2018年）格式化后的数据
        ConcurrentHashMap<String, HashMap<String, List<Double>>> dataMap = getDataMap(dataPath, calYear, calYear);

        // 2、获取每站每年每旬的集合
        HashMap<String, HashMap<String, List<Double>>> formativeDataMap = new HashMap<>();
        for (String siteCode : dataMap.keySet()) { // 循环每一站;
            HashMap<String, List<Double>> meadowMap_avg = new HashMap<>();
            HashMap<String, List<Double>> siteMap = dataMap.get(siteCode);
            for (String currYear : siteMap.keySet()) { // 循环每一年
                List<Double> meadowList_avg = getMeadowList(getMonthMap(siteMap.get(currYear), Integer.parseInt(currYear)));
                meadowMap_avg.put(currYear, meadowList_avg);
            }
            formativeDataMap.put(siteCode, meadowMap_avg);
        }

        // 4、求It

        // 求样本年It
        for (int year = beginYear; year <= endYear; year++) {
            HashMap<String, List<Double>> sampleDatamapforYear = new HashMap<>();
            int nums = 123;
            double result = 0;

            for (String siteCode : formativeDataMap.keySet()) {
                if (siteCode.equals("54732") && year < 1992) {
                    nums--;
                } else if (siteCode.equals("54812") && (year >= 1991 && year <= 1995)) {
                    nums--;
                } else {
                    List<Double> meadowList = formativeSampleDataMap.get(siteCode).get(year + "");
                    sampleDatamapforYear.put(siteCode, meadowList);
                }
            }

            for (String siteCode : formativeDataMap.keySet()) {
                if (sampleDatamapforYear.get(siteCode) != null) {
                    for (int i = 0; i < 36; i++) { // 36旬
                        Double Tij = sampleDatamapforYear.get(siteCode).get(i) / 10;
                        Double _Tij = avgMap.get(siteCode).get(i);
                        Double σ = devMap.get(siteCode).get(i);
                        result += Math.abs((Tij - _Tij) / σ);
                        result = Double.parseDouble(df.format(result));
                    }
                }
            }

            tempIndexList.add(Double.parseDouble(df.format(result / nums)));
        }

        // 求计算年It
        HashMap<String, List<Double>> dataMapForYear = new HashMap<>();
        for (String siteCode : formativeDataMap.keySet()) {
            List<Double> meadowList = formativeDataMap.get(siteCode).get(calYear + "");
            dataMapForYear.put(siteCode, meadowList);
        }
        double result = 0;
        for (String siteCode : formativeDataMap.keySet()) { // 123站
            for (int i = 0; i < 36; i++) { // 36旬
                Double Tij = dataMapForYear.get(siteCode).get(i) / 10;
                Double _Tij = avgMap.get(siteCode).get(i);
                Double σ = devMap.get(siteCode).get(i);
                result += Math.abs(Tij - _Tij) / σ;
                result = Double.parseDouble(df.format(result));
            }
        }
        tempIndexList.add(Double.parseDouble(df.format(result / 123)));

        // 5、将计算结果存入文件
        try {
            // 创建文件
            File ItFlie = new File("results" + File.separator + "TTP" + File.separator + "It.txt");
            if (!ItFlie.getParentFile().exists()) {
                ItFlie.getParentFile().mkdirs();
            }
            if (ItFlie.exists()) {
                ItFlie.delete();
            }
            ItFlie.createNewFile();

            // 输出结果
            StringBuilder sb = new StringBuilder();
            for (Double d : tempIndexList) {
                sb.append(d + "\r\n");
            }

            OutputStream out = new FileOutputStream(ItFlie);
            out.write(sb.toString().getBytes());
            out.close();
            System.out.println("气温年景指数已输出到文件 results/TTP/It.txt中");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return tempIndexList;
    }

    /**
     * 求标准差
     *
     * @param formativeDataMap 按旬格式化后的集合
     * @param avgMap           平均值集合
     * @return 标准差集合
     */
    private HashMap<String, List<Double>> getDevMap(HashMap<String, HashMap<String, List<Double>>> formativeDataMap, HashMap<String, List<Double>> avgMap) {
        DecimalFormat df = new DecimalFormat("#.00");//设置保留位数
        HashMap<String, List<Double>> devMap = new HashMap<>();
        for (String siteCode : formativeDataMap.keySet()) {
            Double[] arr = new Double[36];
            Arrays.fill(arr, 0.0);
            // 记录该年该旬是否缺册
            Integer[] arr_rec = new Integer[36];
            Arrays.fill(arr_rec, 30);

            // 此处除以年数后开根
            // 判断是否为河口检测站，河口站只有19年的数据
            if (siteCode.equals("54732")) {
                for (int currYear = 1992; currYear <= 2010; currYear++) {
                    for (int i = 0; i < 36; i++) {
                        Double Yi = formativeDataMap.get(siteCode).get(currYear + "").get(i) / 10;
                        Double _Y = avgMap.get(siteCode).get(i);
                        arr[i] += Math.pow((Yi - _Y), 2);
                    }
                }

                for (int j = 0; j < arr.length; j++) {
                    arr[j] = Double.parseDouble(df.format((Math.sqrt(arr[j] / 19))));
                }
            } else {
                for (int currYear = 1981; currYear <= 2010; currYear++) {
                    for (int i = 0; i < 36; i++) {
                        Double Yi = formativeDataMap.get(siteCode).get(currYear + "").get(i);
                        Double _Y = avgMap.get(siteCode).get(i);
                        // 判断缺册
                        if (Yi == 32766.0) {
                            arr_rec[i]--;
                        } else {
                            arr[i] += Math.pow((Yi - _Y), 2);
                        }
                    }
                }

                for (int j = 0; j < arr.length; j++) {
                    arr[j] = Double.parseDouble(df.format((Math.sqrt(arr[j] / arr_rec[j]))));
                }
            }

            devMap.put(siteCode, Arrays.asList(arr));
        }

        // 将标准差存入dev.xls
        try {
            // 1、创建文件夹
            File avgFile = new File("results" + File.separator + "TTP");
            if (!avgFile.getParentFile().exists()) {
                avgFile.getParentFile().mkdirs();
            }
            if (!avgFile.exists()) {
                avgFile.mkdirs();
            }

            // 2、创建Excel工作簿
            HSSFWorkbook wb = new HSSFWorkbook();
            HSSFSheet sheet = wb.createSheet("标准差");
            HSSFRow row = sheet.createRow(0);
            for (int xun = 1; xun <= 36; xun++) {
                HSSFCell cell = row.createCell(xun);
                cell.setCellValue(xun + "旬");
            }

            // 3、遍历集合输出数据
            int rowNum = 1;
            for (String siteCode : devMap.keySet()) {
                row = sheet.createRow(rowNum); // 行 - 站
                HSSFCell siteCell = row.createCell(0);
                siteCell.setCellValue(siteCode);

                int cellNum = 1;
                for (Double data : avgMap.get(siteCode)) {
                    HSSFCell cell = row.createCell(cellNum);
                    cell.setCellValue(data);
                    cellNum++;
                }
                rowNum++;
            }

            // 4、输出workbook
            FileOutputStream output = new FileOutputStream("results" + File.separator + "TTP" + File.separator + "dev.xls");
            wb.write(output);
            output.flush();
            output.close();
            System.out.println("标准差值已输出到文件 results/TTP/dev.xls中");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return devMap;
    }

    /**
     * 求平均值
     *
     * @param formativeDataMap 按旬格式化后的集合
     * @return 平均值集合
     */
    private HashMap<String, List<Double>> getAvgMap(HashMap<String, HashMap<String, List<Double>>> formativeDataMap) {
        DecimalFormat df = new DecimalFormat("#.00");//设置保留位数
        HashMap<String, List<Double>> avgMap = new HashMap<>();
        for (String siteCode : formativeDataMap.keySet()) { // 循环站
            Double[] arr = new Double[36];
            Arrays.fill(arr, 0.0);
            // 记录该年该旬是否缺册
            Integer[] arr_rec = new Integer[36];
            Arrays.fill(arr_rec, 30);
            HashMap<String, List<Double>> siteMap = formativeDataMap.get(siteCode);

            for (String year : siteMap.keySet()) {
                List<Double> list = siteMap.get(year);
                for (int i = 0; i < list.size(); i++) {
                    if (list.get(i) == 32766.0) {
                        arr_rec[i]--;
                    } else {
                        arr[i] += list.get(i);
                    }
                }
            }

            // 判断是否为河口检测站，河口站只有19年的数据
            if (siteCode.equals("54732")) {
                for (int j = 0; j < arr.length; j++) {
                    arr[j] = Double.parseDouble(df.format(arr[j] / 19 / 10));
                }
            } else {
                for (int j = 0; j < arr.length; j++) {
                    arr[j] = Double.parseDouble(df.format(arr[j] / arr_rec[j] / 10));
                }
            }

            avgMap.put(siteCode, Arrays.asList(arr));
        }

        // 将平均值存入文件avg.xls
        try {
            // 1、创建文件夹
            File avgFile = new File("results" + File.separator + "TTP");
            if (!avgFile.getParentFile().exists()) {
                avgFile.getParentFile().mkdirs();
            }
            if (!avgFile.exists()) {
                avgFile.mkdirs();
            }

            // 2、创建Excel工作簿
            HSSFWorkbook wb = new HSSFWorkbook();
            HSSFSheet sheet = wb.createSheet("平均值");
            HSSFRow row = sheet.createRow(0);
            for (int xun = 1; xun <= 36; xun++) {
                HSSFCell cell = row.createCell(xun);
                cell.setCellValue(xun + "旬");
            }

            // 3、遍历集合输出数据
            int rowNum = 1;
            for (String siteCode : avgMap.keySet()) {
                row = sheet.createRow(rowNum); // 行 - 站
                HSSFCell siteCell = row.createCell(0);
                siteCell.setCellValue(siteCode);

                int cellNum = 1;
                for (Double data : avgMap.get(siteCode)) {
                    HSSFCell cell = row.createCell(cellNum);
                    cell.setCellValue(data);
                    cellNum++;
                }
                rowNum++;
            }

            // 4、输出workbook
            FileOutputStream output = new FileOutputStream("results" + File.separator + "TTP" + File.separator + "avg.xls");
            wb.write(output);
            output.flush();
            output.close();
            System.out.println("平均值已输出到文件 results/TTP/avg.xls中");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return avgMap;
    }

    /**
     * 获取格式化后的按旬分组的Map集合
     *
     * @param dataMap 所有数据的集合
     * @return formativeDataMap
     */
    private HashMap<String, HashMap<String, List<Double>>> getFormativeDataMap(ConcurrentHashMap<String, HashMap<String, List<Double>>> dataMap) {
        HashMap<String, HashMap<String, List<Double>>> formativeDataMap = new HashMap<>();

        for (String siteCode : dataMap.keySet()) { // 循环每一站;
            HashMap<String, List<Double>> meadowMap_avg = new HashMap<>();
            HashMap<String, List<Double>> siteMap = dataMap.get(siteCode);

            // 按旬分组
            for (String currYear : siteMap.keySet()) { // 循环每一年
                List<Double> meadowList_avg = getMeadowList(getMonthMap(siteMap.get(currYear), Integer.parseInt(currYear)));
                meadowMap_avg.put(currYear, meadowList_avg);
            }
            formativeDataMap.put(siteCode, meadowMap_avg);
        }

        return formativeDataMap;
    }

    /**
     * 读文件获取全部数据，将数据存入集合
     *
     * @param dataPath 数据文件路径
     * @return 返回存有全部数据的集合
     */

    private ConcurrentHashMap<String, HashMap<String, List<Double>>> getDataMap(String dataPath, int beginYear, int endYear) {
        // 结果集
        ConcurrentHashMap<String, HashMap<String, List<Double>>> dataMap = new ConcurrentHashMap();

        // 加载驱动、连接数据库并访问
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // 所有站名的数组，除54732
        int[] siteArr = {54709, 54712, 54714, 54715, 54716,
                54718, 54722, 54723, 54724, 54725, 54726, 54727,
                54728, 54729, 54730, 54731, 54734, 54736, 54737,
                54738, 54744, 54749, 54751, 54752, 54753, 54755,
                54759, 54764, 54765, 54766, 54774, 54776, 54777,
                54778, 54802, 54803, 54805, 54806, 54807, 54808,
                54810, 54811, 54814, 54815, 54816, 54818, 54819,
                54821, 54822, 54823, 54824, 54825, 54826, 54827,
                54828, 54829, 54830, 54831, 54832, 54833, 54834,
                54835, 54836, 54837, 54841, 54842, 54843, 54844,
                54846, 54848, 54849, 54851, 54852, 54853, 54855,
                54857, 54861, 54863, 54871, 54904, 54905, 54906,
                54907, 54908, 54909, 54910, 54911, 54912, 54913,
                54914, 54915, 54916, 54917, 54918, 54919, 54920,
                54921, 54922, 54923, 54925, 54927, 54929, 54932,
                54935, 54936, 54938, 54939, 54940, 54943, 54945,
                58002, 58003, 58011, 58020, 58021, 58022, 58024, 58025, 58030, 58032, 58034, 54812};

        String dbURL = "jdbc:ucanaccess://" + dataPath;

        try (
                Connection connection = DriverManager.getConnection(dbURL);
                Statement stmt = connection.createStatement()) {
            // 创建并读取dbcp连接池的配置文件
            createAndAlterProperties(dbURL);
            Properties properties = new Properties();
            properties.load(new FileReader("./dbcp_ttp.properties"));
            DataSource dataSource = BasicDataSourceFactory.createDataSource(properties);

            // 多线程读Access数据库
            CountDownLatch latch = new CountDownLatch(122);
            ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES,
                    new LinkedBlockingDeque<>());
            for (int site : siteArr) {
                int finalBeginYear = beginYear;
                poolExecutor.execute(() -> {
                    // 从链接池获取链接
                    try (Connection conn = dataSource.getConnection();
                         Statement statement = conn.createStatement()) {

                        HashMap<String, List<Double>> DataMapForYear = new HashMap<>();
                        for (int y = finalBeginYear; y <= endYear; y++) {
                            ArrayList<Double> list = new ArrayList<>();
                            for (int month = 1; month <= 12; month++) {
                                ResultSet rs = statement.executeQuery("select * from [" + site + "] where 年 = " + y + " and 月 = " + month);
                                while (rs.next()) {
                                    list.add(Double.parseDouble(rs.getString("数值")));
                                }
                            }
                            DataMapForYear.put(y + "", list);
                        }
                        dataMap.put(site + "", DataMapForYear);
                        latch.countDown();
                    } catch (SQLException sqle) {
                        sqle.printStackTrace();
                    }
                });
            }

            // 单独读取54732站数据
            if (beginYear < 1992) {
                beginYear = 1992;
            }
            HashMap<String, List<Double>> DataMapForYear = new HashMap<>();
            for (int y = beginYear; y <= endYear; y++) {
                ArrayList<Double> list = new ArrayList<>();
                for (int month = 1; month <= 12; month++) {
                    ResultSet rs = stmt.executeQuery("select * from [" + 54732 + "] where 年 = " + y + " and 月 = " + month);
                    while (rs.next()) {
                        list.add(Double.parseDouble(rs.getString("数值")));
                    }
                }
                DataMapForYear.put(y + "", list);
            }
            dataMap.put(54732 + "", DataMapForYear);
            // 所有数据读完之前主线程等待
            latch.await();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return dataMap;
    }

    /**
     * 将每日的数据按月分组
     *
     * @param dayList 每日的数据List
     * @param year    当前年
     * @return 每年每月分组的Map
     */
    private Map<Integer, List<Double>> getMonthMap(List<Double> dayList, Integer year) {
        // 计算每旬的平均气温
        Map<Integer, List<Double>> monthMap = new HashMap<>();
        int[] day = {31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int[] day_leap = {31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int begin;
        int end = -1;
        // 判断闰年
        if (year % 4 == 0 && year % 100 != 0 || year % 400 == 0) {
            for (int i = 0; i < 12; i++) {
                List<Double> monthList = new ArrayList<>();
                // 更新起止索引位置
                begin = end + 1;
                end = begin + day_leap[i] - 1;
                for (int j = begin; j <= end; j++) {
                    monthList.add(dayList.get(j));
                }
                monthMap.put(i + 1, monthList);
            }
        } else {
            for (int i = 0; i < 12; i++) {
                List<Double> monthList = new ArrayList<>();
                // 更新起止索引位置
                begin = end + 1;
                end = begin + day[i] - 1;
                for (int j = begin; j <= end; j++) {
                    monthList.add(dayList.get(j));
                }
                monthMap.put(i + 1, monthList);
            }
        }

        return monthMap;
    }

    /**
     * 获取每旬的平均气温
     *
     * @param monthMap 按月分组的Map集合
     * @return 返回按旬分组的List集合
     */
    private List<Double> getMeadowList(Map<Integer, List<Double>> monthMap) {
        List<Double> meadowList = new ArrayList<>();
        for (List<Double> l : monthMap.values()) {
            int first = 0;
            int second = 0;
            int third = 0;

            // 缺册计数器
            int first_rec = 10;
            int second_rec = 10;
            int third_rec = l.size() - 20;

            // 每个月的第一旬平均气温
            for (int i = 0; i < 10; i++) {
                if (!(l.get(i) == 32766)) { // 判断该天是否缺册
                    first += l.get(i);
                } else {
                    first_rec--;
                }
            }

            if (first_rec == 0) { // 判断该旬数据是否缺册
                meadowList.add(32766.0);
            } else {
                meadowList.add((double) (first / first_rec));
            }

            // 每个月的第二旬平均气温
            for (int i = 10; i < 20; i++) {
                if (!(l.get(i) == 32766)) {
                    second += l.get(i);
                } else {
                    second_rec--;
                }
            }

            if (second_rec == 0) { // 判断该旬数据是否缺册
                meadowList.add(32766.0);
            } else {
                meadowList.add((double) (second / second_rec));
            }

            // 每个月的第三旬平均气温
            for (int i = 20; i < l.size(); i++) {
                if (!(l.get(i) == 32766)) {
                    third += l.get(i);
                } else {
                    third_rec--;
                }
            }

            if (third_rec == 0) { // 判断该旬数据是否缺册
                meadowList.add(32766.0);
            } else {
                meadowList.add((double) (third / third_rec));
            }
        }

        return meadowList;
    }

    /**
     * 设置DBCP连接池的配置参数
     *
     * @param dbURL 数据路径
     */
    public void createAndAlterProperties(String dbURL) throws IOException {

        File file = new File("./dbcp_ttp.properties");
        if (!file.exists()) {
            file.createNewFile();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("driverClassName=net.ucanaccess.jdbc.UcanaccessDriver\r\n");
        sb.append("url=" + dbURL + "\r\n");
        sb.append("maxActive=30\r\n");
        sb.append("maxIdle=-1");
        OutputStream out = new FileOutputStream(file);
        out.write(sb.toString().getBytes());
        out.close();
    }
}


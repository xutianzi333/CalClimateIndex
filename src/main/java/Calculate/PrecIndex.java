package Calculate;

import org.apache.commons.dbcp.BasicDataSourceFactory;
import org.apache.commons.math3.special.Gamma;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;

import javax.sql.DataSource;
import java.io.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class PrecIndex {
    static DecimalFormat df = new DecimalFormat("#.0");//设置保留位数

    /**
     * 计算降水年景指数
     *
     * @param dataPathRRR 数据路径
     * @param beginYear   样本年起始年
     * @param endYear     样本年结束年
     * @param calYear     计算年
     * @return 返回样本数据降水年景指数与计算数据降水年景指数
     */
    public List<Double> calculate(String dataPathRRR, int beginYear, int endYear, int calYear) {
        // 1、读文件获取数据
        HashMap<String, List<Double>> sampleDataMap = getDataMap(dataPathRRR, beginYear, endYear); // 样本年数据
        HashMap<String, List<Double>> DataMap = getDataMap(dataPathRRR, calYear, calYear); // 计算年数据
        // 2、将数据按旬分组
        HashMap<String, HashMap<String, List<Double>>> formativeSampleDataMap = getFormativeMap(true, sampleDataMap, beginYear, endYear);
        HashMap<String, HashMap<String, List<Double>>> formativeDataMap = getFormativeMap(false, DataMap, calYear, calYear);
        // 3、计算SPI值
        List<HashMap<String, HashMap<String, List<Double>>>> parmAndspiList = calParmAndSPI(beginYear, endYear, formativeSampleDataMap);
        // 4、计算降水年景指数
        return calPrecIndex(calYear, beginYear, formativeDataMap, parmAndspiList);
    }

    /**
     * @param beginYear
     * @param formativeDataMap
     * @param parmAndspiList
     * @return
     */
    private List<Double> calPrecIndex(int calYear, int beginYear, HashMap<String, HashMap<String, List<Double>>> formativeDataMap, List<HashMap<String, HashMap<String, List<Double>>>> parmAndspiList) {
        DecimalFormat df = new DecimalFormat("#.00");//设置保留位数
        DecimalFormat df_spi = new DecimalFormat("#.000");//设置保留位数

        ArrayList<Double> precIndexList = new ArrayList<>();
        Double valArr[] = new Double[30];
        Arrays.fill(valArr, 0.0);
        HashMap<String, HashMap<String, List<Double>>> sampleSPIMap = parmAndspiList.get(0);
        HashMap<String, HashMap<String, List<Double>>> parmMap = parmAndspiList.get(1);

        // 计算样本年的降水年景指数
        for (String siteCode : sampleSPIMap.keySet()) { // 循环站
            HashMap<String, List<Double>> mapForSite = sampleSPIMap.get(siteCode); // 每站30年的数据集合
            int i = 0; // valArr数组索引，每个索引代表一年
            for (String year : mapForSite.keySet()) {
                List<Double> listForYear = mapForSite.get(year); // 每站每年36旬SPI的集合
                double sum = 0;
                for (double spi : listForYear) {
                    sum += Math.abs(spi);
                }
                valArr[i] += sum;
                i++;
            }
        }
        for (int i = 0; i < valArr.length; i++) {
            int year = i + beginYear;
            Double result;
            if (year < 1991 || (year >= 1992 && year <= 1995)) {
                result = Double.valueOf(valArr[i] / 122);
            } else if (year >= 1996) {
                result = Double.valueOf(valArr[i] / 123);
            } else {
                result = Double.valueOf(valArr[i] / 121);
            }
            precIndexList.add(Double.parseDouble(df.format(result)));
        }

        // 用参数计算计算年的降水年景指数
        HashMap<String, List<Double>> spiMap = new HashMap<>();
        for (String siteCode : formativeDataMap.keySet()) { // 循环站
            HashMap<String, List<Double>> mapForSite = formativeDataMap.get(siteCode);
            for (List<Double> list : mapForSite.values()) { // 循环年，只有一年的数据
                int xun = 1;
                List<Double> spiList = new ArrayList<>();
                for (double xi : list) { // 循环旬
                    // 计算SPI
                    double A = parmMap.get(siteCode).get(xun + "").get(0);
                    double β = parmMap.get(siteCode).get(xun + "").get(2);
                    double γ = parmMap.get(siteCode).get(xun + "").get(1);
                    double p = 0, t = 0, s = 0, SPI = 0; // f(x)
                    if (xi == 0.0) { // 当xi为0时
                        p = 1.0 / 31.0;
                    } else { // 当x不为0时
                        p = Gamma.regularizedGammaP(γ, xi / β);
                    }
                    // 求t、SPI
                    if (p > 0.5) {
                        p = 1.0 - p;
                        t = Math.sqrt(Math.log(1 / (p * p)));
                        s = 1;
                        SPI = t - (c0 + c1 * t + c2 * Math.pow(t, 2)) / (1 + d1 * t + d2 * Math.pow(t, 2) + d3 * Math.pow(t, 3));
                    } else if (p <= 0.5) {
                        t = Math.sqrt(Math.log(1 / (p * p)));
                        s = -1;
                        SPI = -(t - (c0 + c1 * t + c2 * Math.pow(t, 2)) / (1 + d1 * t + d2 * Math.pow(t, 2) + d3 * Math.pow(t, 3)));
                    }
                    spiList.add(SPI);
                    // 下一旬
                    xun++;
                }
                spiMap.put(siteCode, spiList);
            }
        }

        // 把计算年的SPI输出到Excel文件中
        // 创建Excel工作簿
        HSSFWorkbook wb = new HSSFWorkbook();
        // 站名排序
        Object[] siteCodes = spiMap.keySet().toArray();
        Arrays.sort(siteCodes);
        for (Object siteCode : siteCodes) { // 循环站
            HSSFSheet sheet = wb.createSheet((String) siteCode);

            HSSFRow firstRow = sheet.createRow(0);
            HSSFCell yearCell = firstRow.createCell(1);
            yearCell.setCellValue(calYear + "年");
            int rowNum = 1;
            for (Double val : spiMap.get(siteCode)) {
                HSSFRow row = sheet.createRow(rowNum);
                HSSFCell firstCell = row.createCell(0);
                HSSFCell cell = row.createCell(1);
                firstCell.setCellValue("第" + rowNum + "旬");
                cell.setCellValue(Double.parseDouble(df_spi.format(val)));
                rowNum++;
            }
            // 4、输出workbook
            try {
                FileOutputStream output = new FileOutputStream("results" + File.separator + "RRR" + File.separator + "SPI_" + calYear + ".xls");
                wb.write(output);
                output.flush();
                output.close();
            } catch (FileNotFoundException fe) {
                System.out.println("另一个程序正在使用此文件，进程无法访问。");
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        System.out.println("计算年SPI值已输出到文件 results/RRR/SPI_" + calYear + ".xls中");

        Double result = new Double(0);
        for (List<Double> list : spiMap.values()) {
            for (double val : list) {
                result += Double.valueOf(Math.abs(val));
            }
        }

        result = Double.parseDouble(df.format(result / 123));
        precIndexList.add(result);

        // 将计算结果存入文件
        try {
            // 创建文件
            File ItFlie = new File("results" + File.separator + "RRR" + File.separator + "Ip.txt");
            if (!ItFlie.getParentFile().exists()) {
                ItFlie.getParentFile().mkdirs();
            }
            if (ItFlie.exists()) {
                ItFlie.delete();
            }
            ItFlie.createNewFile();

            // 输出结果
            StringBuilder sb = new StringBuilder();
            for (Double d : precIndexList) {
                sb.append(d + "\r\n");
            }

            OutputStream out = new FileOutputStream(ItFlie);
            out.write(sb.toString().getBytes());
            out.close();
            System.out.println("降水年景指数已输出到文件 results/RRR/Ip.txt中");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return precIndexList;
    }

    static final double c0 = 2.515517, c1 = 0.802853,
            c2 = 0.010328, d1 = 1.432788, d2 = 0.189269, d3 = 0.001308;

    private List<HashMap<String, HashMap<String, List<Double>>>> calParmAndSPI(int beginYear, int endYear, HashMap<String, HashMap<String, List<Double>>> formativeSampleDataMap) {
        DecimalFormat df = new DecimalFormat("#.000");//设置保留位数

        HashMap<String, HashMap<String, List<Double>>> parmMap = new HashMap<>();
        HashMap<String, HashMap<String, List<Double>>> spiMap = new HashMap<>();


        for (String siteCode : formativeSampleDataMap.keySet()) { // 循环站
            HashMap<String, List<Double>> formativeSampleDataMapForOneSite = formativeSampleDataMap.get(siteCode);

            HashMap<String, List<Double>> parmMapForOneSite = new HashMap<>();
            HashMap<String, List<Double>> spiMapForOneSite = new HashMap<>();

            for (String xun : formativeSampleDataMapForOneSite.keySet()) {
                List<Double> formativeSampleDataList = formativeSampleDataMapForOneSite.get(xun); // 循环旬
                // 1、求α（A）、β、γ
                double A, β, γ, ave, sx, sx2; // sx2是lg(xi) sigma之和
                double n = 0; // 总样本数
                double m = 0; // 降水量为0的样本数
                // 1.1、求平均值sx2;求ave
                sx2 = 0.0;
                sx = 0.0;
                for (int i = 0; i < formativeSampleDataList.size(); i++) {
                    if (formativeSampleDataList.get(i) != null) { // 有些站没有30年的数据，故判断
                        double xi = formativeSampleDataList.get(i);
                        // 当xi为0时
                        if (xi == 0.0) {
                            m++;
                        } else {
                            sx += xi;
                            sx2 += Math.log(xi);
                        }
                        n++;
                    }
                }
                ave = sx / n;

                List<Double> parmList = new ArrayList<>();
                // 1.2、求A，γ，β
                A = Math.log(ave) - (sx2 / n);
                γ = (1 + Math.sqrt(1 + ((4 * A) / 3))) / (4 * A);
                β = ave / γ;
                parmList.add(A);
                parmList.add(γ);
                parmList.add(β);

                List<Double> SPIList = new ArrayList<>();
                // 2、求gamma(γ)与P
                for (int i = 0; i < n; i++) { // 循环n年的数据
                    if (formativeSampleDataList.get(i) != null) {
                        double xi = formativeSampleDataList.get(i);
                        double p = 0, t = 0, s = 0, SPI = 0; // f(x)
                        // 当xi为0时
                        if (xi == 0.0) {
                            p = m / n;
                        } else {
                            // 求0到xi f(x)的定积分
                            p = Gamma.regularizedGammaP(γ, xi / β);
                        }
                        // 3、求t、SPI
                        if (p > 0.5) {
                            p = 1.0 - p;
                            t = Math.sqrt(Math.log(1 / (p * p)));
                            s = 1;
                            SPI = t - (c0 + c1 * t + c2 * Math.pow(t, 2)) / (1 + d1 * t + d2 * Math.pow(t, 2) + d3 * Math.pow(t, 3));
                        } else if (p <= 0.5) {
                            t = Math.sqrt(Math.log(1 / (p * p)));
                            s = -1;
                            SPI = -(t - (c0 + c1 * t + c2 * Math.pow(t, 2)) / (1 + d1 * t + d2 * Math.pow(t, 2) + d3 * Math.pow(t, 3)));
                        }
                        SPIList.add(Double.parseDouble(df.format(SPI)));
                    }
                }
                spiMapForOneSite.put(xun, SPIList);
                parmMapForOneSite.put(xun, parmList);
            }
            spiMap.put(siteCode, spiMapForOneSite);
            parmMap.put(siteCode, parmMapForOneSite);
        }
        try {
            // 1、创建文件夹
            File avgFile = new File("results" + File.separator + "RRR");
            if (!avgFile.getParentFile().exists()) {
                avgFile.getParentFile().mkdirs();
            }
            if (!avgFile.exists()) {
                avgFile.mkdirs();
            }

            // 2、创建Excel工作簿
            HSSFWorkbook wb = new HSSFWorkbook();
            // 站名排序
            Object[] siteCodes = spiMap.keySet().toArray();
            Arrays.sort(siteCodes);
            for (Object siteCode : siteCodes) { // 循环站
                HSSFSheet sheet = wb.createSheet((String) siteCode);
                HSSFRow row = sheet.createRow(0);
                for (int year = endYear; year >= beginYear; year--) {
                    HSSFCell cell = row.createCell(endYear - year + 1);
                    cell.setCellValue((beginYear + (endYear - year)) + "年");
                }
                int rowNum = 1;
                // 旬排序
                Object[] xuns = spiMap.get(siteCode).keySet().toArray();
                for (int i = 0; i < xuns.length; i++) {
                    xuns[i] = Integer.parseInt((String) xuns[i]);
                }
                Arrays.sort(xuns);
                for (Object xun : xuns) {
                    row = sheet.createRow(rowNum);
                    HSSFCell xunCell = row.createCell(0);
                    xunCell.setCellValue("第" + xun + "旬");

                    List<Double> l = spiMap.get(siteCode).get(xun + "");
                    int cellNum = 1;
                    for (double v : l) { // 循环年
                        HSSFCell cell = row.createCell(cellNum);
                        cell.setCellValue(v);
                        cellNum++;
                    }
                    rowNum++;
                }
                // 4、输出workbook
                FileOutputStream output = new FileOutputStream("results" + File.separator + "RRR" + File.separator + "SPI.xls");
                wb.write(output);
                output.flush();
                output.close();
            }
            System.out.println("样本年SPI值已输出到文件 results/RRR/SPI.xls中");
        } catch (Exception e) {
            e.printStackTrace();
        }

        // spiMap转二维数组
        HashMap<String, HashMap<String, List<Double>>> spiMap_trans = getTransMap(spiMap);

        List<HashMap<String, HashMap<String, List<Double>>>> parmAndspiList = new ArrayList<>();
        parmAndspiList.add(spiMap_trans);
        parmAndspiList.add(parmMap);

        return parmAndspiList;
    }

    /**
     * 二维数组转置
     *
     * @param oldMap 需要转置的map
     * @return 转置后的map
     */
    private HashMap<String, HashMap<String, List<Double>>> getTransMap(HashMap<String, HashMap<String, List<Double>>> oldMap) {
        HashMap<String, HashMap<String, List<Double>>> transMap = new HashMap<>();
        Double[][] o1 = new Double[36][30];
        Double[][] o2 = new Double[30][36];
        for (String siteCode : oldMap.keySet()) {
            Map<String, List<Double>> map = oldMap.get(siteCode);
            int m = 0;
            for (String xun : map.keySet()) {
                List<Double> list = map.get(xun);
                int n = 0;
                for (Double val : list) {
                    o1[m][n] = val;
                    n++;
                }
                m++;
            }
            // 将二维数组转置
            for (int i = 0; i < o1[i].length; i++) {
                for (int j = 0; j < o1.length; j++) {
                    o2[i][j] = o1[j][i];
                }
            }
            // 转置后
            HashMap<String, List<Double>> map_trans = new HashMap<>();
            for (int i = 0; i < o2.length; i++) {
                map_trans.put(i + 1 + "", Arrays.asList(o2[i]));
            }
            transMap.put(siteCode, map_trans);
        }
        return transMap;
    }

    /**
     * 将样本年降水指数按旬分组，用于计算α、β、γ等参数
     *
     * @param dataMap   原始数据集合
     * @param beginYear 起始年
     * @param endYear   结束年
     * @return 分旬的降水指数集合
     */
    private HashMap<String, HashMap<String, List<Double>>> getFormativeMap(boolean trans, HashMap<String, List<Double>> dataMap, int beginYear, int endYear) {
        HashMap<String, HashMap<String, List<Double>>> formativeMap = new HashMap<>(); // 结果集合
        List<Integer> indexList = new ArrayList<>(); // 每旬旬末索引集合

        // 1、记录旬末索引
        int index = 30; // 起始年索引（例：1981-01-01）
        for (int year = beginYear; year <= endYear; year++) {
            indexList.addAll(groupByXun(index, year));
            if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                index = index + 366;
            } else { // 非闰年
                index = index + 365;
            }
        }

        // 2、遍历数据集合
        for (String siteCode : dataMap.keySet()) {
            List<Double> dataList = dataMap.get(siteCode); // 数据集合
            HashMap<String, List<Double>> formativeMapForOneSite = new HashMap<>(); // 单站30年的数据集合
            List<Double> formativeListForOneYear = new ArrayList<>(); // 单站单年的数据集合
            int currYear;
            // 54732站和54812站特殊处理
            if ("54732".equals(siteCode)) {// 54732的数据从92年开始
                currYear = Math.max(beginYear, 1992);

                // 54732站的旬末索引
                List<Integer> indexList_54732 = new ArrayList<>();
                int index_54732 = 0;
                for (int year = currYear; year <= endYear; year++) {
                    indexList_54732.addAll(groupByXun(index_54732, year));
                    if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                        index_54732 = index_54732 + 366;
                    } else { // 非闰年
                        index_54732 = index_54732 + 365;
                    }
                }

                // 54732站集合按旬分组
                for (int i = 0; i < indexList_54732.size(); i++) { // 遍历数据
                    // 计算每旬末30天降水量之和
                    double data = 0;

                    if (i == 0) {
                        for (int j = indexList_54732.get(i) - 10; j < indexList_54732.get(i); j++) {
                            data += dataList.get(j);
                        }
                    } else if (i == 1) {
                        for (int j = indexList_54732.get(i) - 20; j < indexList_54732.get(i); j++) {
                            data += dataList.get(j);
                        }
                    } else {
                        for (int j = indexList_54732.get(i) - 30; j < indexList_54732.get(i); j++) {
                            data += dataList.get(j);
                        }
                    }
                    formativeListForOneYear.add(Double.parseDouble(df.format(data)));

                    if ((i + 1) % 36 == 0) { // 判断是否在一年内
                        formativeMapForOneSite.put(currYear + "", formativeListForOneYear); // 保存1站1年36旬的数据
                        currYear++; // 记录下一年
                        formativeListForOneYear = new ArrayList<>(); // 清空集合
                    }
                }
            } else if ("54812".equals(siteCode)) {
                List<Integer> indexList_54812 = new ArrayList<>(); // 54812站的旬末索引
                // 求54812站的旬末索引
                if (beginYear < 1990) {
                    index = 30;
                    for (int year = beginYear; year <= 1990; year++) {
                        indexList_54812.addAll(groupByXun(index, year));
                        if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                            index = index + 366;
                        } else { // 非闰年
                            index = index + 365;
                        }
                    }
                    index += (365 * 4) + 366;
                    for (int year = 1996; year <= endYear; year++) {
                        indexList_54812.addAll(groupByXun(index, year));
                        if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                            index = index + 366;
                        } else { // 非闰年
                            index = index + 365;
                        }
                    }
                } else if (beginYear <= 1995 && beginYear >= 1990) {
                    index = 30;
                    for (int year = beginYear; year <= 1995; year++) {
                        if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                            index = index + 366;
                        } else { // 非闰年
                            index = index + 365;
                        }
                    }

                    for (int year = 1996; year <= endYear; year++) {
                        indexList_54812.addAll(groupByXun(index, year));
                        if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                            index = index + 366;
                        } else { // 非闰年
                            index = index + 365;
                        }
                    }

                } else {
                    index = 30;
                    for (int year = beginYear; year <= endYear; year++) {
                        indexList_54812.addAll(groupByXun(index, year));
                        if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                            index = index + 366;
                        } else { // 非闰年
                            index = index + 365;
                        }
                    }
                }
                // 54812站集合按旬分组
                currYear = beginYear; // 起始年
                for (int i = 0; i < indexList_54812.size(); i++) { // 遍历数据
                    // 计算每旬末30天降水量之和
                    double data = 0;

                    for (int j = indexList_54812.get(i) - 30; j <= indexList_54812.get(i); j++) {
                        data += dataList.get(j);
                    }
                    formativeListForOneYear.add(Double.parseDouble(df.format(data)));

                    if ((i + 1) % 36 == 0) { // 判断是否在一年内
                        formativeMapForOneSite.put(currYear + "", formativeListForOneYear); // 保存1站1年36旬的数据
                        currYear++; // 记录下一年
                        formativeListForOneYear = new ArrayList<>(); // 清空集合
                    }
                }
            } else {
                currYear = beginYear; // 起始年

                for (int i = 0; i < indexList.size(); i++) { // 遍历数据
                    // 计算每旬末30天降水量之和
                    double data = 0;

                    for (int j = indexList.get(i) - 30; j <= indexList.get(i); j++) {
                        data += dataList.get(j);
                    }
                    formativeListForOneYear.add(Double.parseDouble(df.format(data)));

                    if ((i + 1) % 36 == 0) { // 判断是否在一年内
                        formativeMapForOneSite.put(currYear + "", formativeListForOneYear); // 保存1站1年36旬的数据
                        currYear++; // 记录下一年
                        formativeListForOneYear = new ArrayList<>(); // 清空集合
                    }
                }
            }
            // 将每站的格式化后的数据放入集合
            // formativeMapForOneSite转二维数组
            if (trans) {
                Double[][] o1 = new Double[30][36];
                Double[][] o2 = new Double[36][30];
                int m = 0;
                for (int year = beginYear; year <= endYear; year++) {
                    if (formativeMapForOneSite.get(year + "") != null) {
                        List<Double> list = formativeMapForOneSite.get(year + "");
                        int n = 0;
                        for (Double l : list) {
                            o1[m][n] = l;
                            n++;
                        }
                        m++;
                    }
                }

                // 将二维数组转置
                for (int i = 0; i < 36; i++) {
                    for (int j = 0; j < 30; j++) {
                        o2[i][j] = o1[j][i];
                    }
                }

                // 转置后
                formativeMapForOneSite = new HashMap<>();
                for (int i = 0; i < o2.length; i++) {
                    formativeMapForOneSite.put(i + 1 + "", Arrays.asList(o2[i]));
                }
            }

            formativeMap.put(siteCode, formativeMapForOneSite);
        }

        return formativeMap;
    }

    /**
     * 输入数据文件
     *
     * @param dataPath 文件路径
     * @return 全部数据按站分组集合
     */
    private HashMap<String, List<Double>> getDataMap(String dataPath, int beginYear, int endYear) {
        HashMap<String, List<Double>> dataMap = new HashMap<>(); // 结果集

        // 加载驱动、连接数据库并访问
        try {
            Class.forName("net.ucanaccess.jdbc.UcanaccessDriver");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }

        // 所有站名的数组，除54732、54812
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

        // 加载驱动、连接数据库并访问
        try {
            Connection connection = DriverManager.getConnection(dbURL);
            Statement stmt = connection.createStatement();

            // 创建并读取dbcp连接池的配置文件
            createAndAlterProperties(dbURL);
            Properties properties = new Properties();
            properties.load(new FileReader("./dbcp_rrr.properties"));
            DataSource dataSource = BasicDataSourceFactory.createDataSource(properties);

            // 多线程读Access数据库
            CountDownLatch latch = new CountDownLatch(122);
            ThreadPoolExecutor poolExecutor = new ThreadPoolExecutor(10, 20, 1, TimeUnit.MINUTES,
                    new LinkedBlockingDeque<>());

            // 所有正常站点数据集合
            for (int site : siteArr) {
                int finalBeginYear = beginYear;
                poolExecutor.execute(() -> {
                    try (Connection conn = dataSource.getConnection();
                         Statement statement = conn.createStatement()) {
                        ArrayList<Double> list = new ArrayList<>();
                        int beforYeay = finalBeginYear - 1;
                        ResultSet rs = statement.executeQuery("select * from [" + site + "] where 年 = " + beforYeay + " and 月 = " + 12);
                        while (rs.next()) {
                            list.add(Double.parseDouble(rs.getString("数值")) / 10);
                        }
                        for (int y = finalBeginYear; y <= endYear; y++) {
                            for (int month = 1; month <= 12; month++) {
                                rs = statement.executeQuery("select * from [" + site + "] where 年 = " + y + " and 月 = " + month);
                                while (rs.next()) {
                                    list.add(Double.parseDouble(rs.getString("数值")) / 10);
                                }
                            }
                        }
                        dataMap.put(site + "", list);
                        latch.countDown();
                        System.out.println(latch.getCount());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
            // 54732站
            if (beginYear < 1992) {
                beginYear = 1992;
            }
            ArrayList<Double> list = new ArrayList<>();
            // 93年才有92年12月的数据
            if (beginYear > 1992) {
                ResultSet rs = stmt.executeQuery("select * from [" + 54732 + "] where 年 = " + (beginYear - 1) + " and 月 = 12");
                while (rs.next()) {
                    list.add(Double.parseDouble(rs.getString("数值")) / 10);
                }
            }
            for (int y = beginYear; y <= endYear; y++) {
                for (int month = 1; month <= 12; month++) {
                    ResultSet rs = stmt.executeQuery("select * from [" + 54732 + "] where 年 = " + y + " and 月 = " + month);
                    while (rs.next()) {
                        list.add(Double.parseDouble(rs.getString("数值")) / 10);
                    }
                }
            }
            dataMap.put("54732", list);
            // 所有数据读完之前主线程等待
            latch.await();
            System.out.println("RRR数据读取完毕");
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dataMap;
    }

    /**
     * 记录旬末索引
     *
     * @param index 起始索引
     * @param year  当前年
     * @return 旬末索引
     */
    private ArrayList<Integer> groupByXun(int index, int year) {
        ArrayList<Integer> indexList = new ArrayList<>();
        for (int xun = 1; xun <= 36; xun++) {
            if (xun % 3 == 0) { // 是否是每个月的第三旬
                if (xun == 6) { // 2月
                    if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) { // 闰年
                        index = index + 9;
                    } else { // 非闰年
                        index = index + 8;
                    }
                } else if (xun == 12 || xun == 18 || xun == 27 || xun == 33) { //
                    index = index + 10;
                } else {
                    index = index + 11;
                }
            } else {
                index = index + 10;
            }
            indexList.add(index);
        }
        return indexList;
    }

    /**
     * 设置DBCP连接池的配置参数
     *
     * @param dbURL 数据路径
     */
    public void createAndAlterProperties(String dbURL) throws IOException {

        File file = new File("./dbcp_rrr.properties");
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

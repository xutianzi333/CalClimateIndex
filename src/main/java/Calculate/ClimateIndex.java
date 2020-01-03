package Calculate;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;

public class ClimateIndex {
    public static void main(String[] args) throws InterruptedException {
        // 1、设置参数
        Scanner sc = new Scanner(System.in);
        int beginYear, endYear, calYear;
        String dataPathTTP, dataPathRRR;
        System.out.println("样本年的起始年份：");
        beginYear = sc.nextInt();
        System.out.println("样本年的结束年份：");
        endYear = sc.nextInt();
        System.out.println("需要计算的年份：");
        calYear = sc.nextInt();
        System.out.println("请输入气温数据文件(.mdb)路径:");
        dataPathTTP = sc.next();
        System.out.println("请输入降水数据文件(.mdb)路径:");
        dataPathRRR = sc.next();

        // 2、调用计算方法
        calculate(beginYear, endYear, calYear, dataPathTTP, dataPathRRR);
    }


    public static void calculate(int beginYear, int endYear, int calYear, String dataPathTTP, String dataPathRRR) {
        Scanner sc = new Scanner(System.in);
        DecimalFormat df = new DecimalFormat("#.00"); // 设置保留位数
        TempIndex ti = new TempIndex();
        PrecIndex pi = new PrecIndex();

        // 1、计算气温年景指数
        System.out.println();
        System.out.println("====================气温年景指数计算中===================");
        List<Double> tempIndexDataList = ti.calculate(dataPathTTP, beginYear, endYear, calYear);

        // 2、计算降水景指数
        System.out.println();
        System.out.println("====================降水年景指数计算中====================");
        List<Double> precIndexDataList = pi.calculate(dataPathRRR, beginYear, endYear, calYear);

        // 3.1 计算气候年景指数
        System.out.println();
        System.out.println("====================气候年景指数计算中====================");
        ArrayList<Double> Ic_List = new ArrayList<>(); // 气候年景指数集合
        for (int i = 0; i < tempIndexDataList.size(); i++) {
            Double It = tempIndexDataList.get(i); // 气温年景指数
            Double Ip = precIndexDataList.get(i); // 降水年景指数
            Double Ic = It + 3 * Ip; // 气候年景指数
            Ic_List.add(Double.parseDouble(df.format(Ic))); // 保留两位小数
        }

        try {
            // 创建文件
            File ItFlie = new File("results" + File.separator + "Ic.txt");
            if (!ItFlie.getParentFile().exists()) {
                ItFlie.getParentFile().mkdirs();
            }
            if (ItFlie.exists()) {
                ItFlie.delete();
            }
            ItFlie.createNewFile();

            // 输出结果
            StringBuilder sb = new StringBuilder();
            for (double d : Ic_List) {
                sb.append(d + "\r\n");
            }

            OutputStream out = new FileOutputStream(ItFlie);
            out.write(sb.toString().getBytes());
            out.close();
            System.out.println("降水年景指数已输出到文件 results/Ic.txt中");
        } catch (Exception e) {
            e.printStackTrace();
        }

        Double calIc = Ic_List.get(30);
        Ic_List.remove(30);
        Collections.sort(Ic_List);

        // 3.2 计算百分位数阈值
        double[] pArr = {0.1, 0.3, 0.7, 0.9}; // 百分位数
        double[] pValArr = new double[4]; // 记录阈值的数组
        System.out.println();
        System.out.println("======================百分位数阈值======================");
        System.out.println();
        for (int i = 0; i < pArr.length; i++) {
            pValArr[i] = percentileCalculation(pArr[i], Ic_List);
            System.out.println(pValArr[i]);
        }

        // 3.3 划分气候年景等级
        System.out.println();
        System.out.print(calYear + "气候年景指数：" + calIc + " ");
        if (calIc <= pValArr[0]) { // 小于P10
            System.out.println("好");
        } else if (calIc > pValArr[0] && calIc <= pValArr[1]) { // P10与P30之间
            System.out.println("较好");
        } else if (calIc > pValArr[1] && calIc <= pValArr[2]) { // P30与P70之间
            System.out.println("一般");
        } else if (calIc > pValArr[2] && calIc <= pValArr[3]) { // P70与P90之间
            System.out.println("较差");
        } else { // 大于P90
            System.out.println("差");
        }
    }

    /**
     * 计算百分位数
     *
     * @param p        百分位数
     * @param dataList 样本序列集合
     * @return 返回结果 - 百分位数阈值
     */
    private static double percentileCalculation(double p, ArrayList<Double> dataList) {
        DecimalFormat df = new DecimalFormat("#.00");//设置保留位数

        int n = dataList.size(); // 序列总数
        int j = (int) (p * n + (1 + p) / 3); // 第j个序列数
        double y = p * n + (1 + p) / 3 - j;
        double qp = (1 - y) * dataList.get(j) + y * dataList.get(j + 1); // 分位数

        return Double.parseDouble(df.format(qp));
    }
}

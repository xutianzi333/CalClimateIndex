import Calculate.ClimateIndex;
import Calculate.PrecIndex;
import Calculate.TempIndex;
import org.junit.jupiter.api.Test;

public class CalTest {
    @Test
    public void calTest() {
        int beginYear, endYear, calYear;
        String dataPathTTP, dataPathRRR;
        beginYear = 1981;
        endYear = 2010;
        calYear = 2018;
        dataPathTTP = "E:/TTP.mdb";
        dataPathRRR = "E:/RRR.mdb";

        ClimateIndex.calculate(beginYear, endYear, calYear, dataPathTTP, dataPathRRR);
    }

    @Test
    public void calTempTest() {

        int beginYear, endYear, calYear;
        String dataPathTTP;
        beginYear = 1981;
        endYear = 2010;
        calYear = 2018;
        dataPathTTP = "E:/TTP.mdb";

        TempIndex t = new TempIndex();
        t.calculate(dataPathTTP, beginYear, endYear, calYear);
    }

    @Test
    public void calPrecTest() {

        int beginYear, endYear, calYear;
        String dataPathTTP;
        beginYear = 1981;
        endYear = 2010;
        calYear = 2018;
        dataPathTTP = "E:/RRR.mdb";

        PrecIndex p = new PrecIndex();
        p.calculate(dataPathTTP, beginYear, endYear, calYear);
    }
}

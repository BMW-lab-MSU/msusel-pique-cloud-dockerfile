package calibration;


import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class NaiveCloudBenchmarker extends CloudBenchmarker{

    @Override
    public String getName() {
        return this.getClass().getCanonicalName();
    }

    @Override
    public Map<String, BigDecimal[]> calculateThresholds(Map<String, ArrayList<BigDecimal>> measureBenchmarkData) {
        //for the pdf function, store all values as the thresholds. PDF function operation occurs in the evaluator run
        Map<String, BigDecimal[]> measureThresholds = new HashMap<>();
        measureBenchmarkData.forEach((measureName, measureValues) -> {
            BigDecimal[] measureValuesArray = new BigDecimal[measureValues.size()];
            measureThresholds.putIfAbsent(measureName, measureValues.toArray(measureValuesArray));
        });
        return measureThresholds;
    }
}

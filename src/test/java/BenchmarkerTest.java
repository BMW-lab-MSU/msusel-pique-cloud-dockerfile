import calibration.CloudBenchmarker;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import pique.analysis.ITool;
import pique.evaluation.Project;
import pique.model.QualityModel;
import pique.model.QualityModelImport;
import pique.utility.PiqueProperties;
import tool.GrypeWrapper;
import utilities.helperFunctions;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class BenchmarkerTest {

    private static CloudBenchmarker benchmarker;
    private static Properties prop;



    public BenchmarkerTest(){

    }

    @BeforeClass
    public static void setup(){
        try {
            benchmarker = new CloudBenchmarker();
            prop = PiqueProperties.getProperties("./src/test/resources/pique-test-properties.properties");
        }catch (Exception e){
            System.out.println("Error initializing properties in setup");
        }
    }


    @Test
    public void testBenchmarkImport(){

        Set<Path> dockerImages = helperFunctions.getDockerImagesToAnalyze(Paths.get(prop.getProperty("benchmark.repo")));

    }

    @Test
    public void testDeriveThresholds(){



        Path blankqmFilePath = Paths.get(prop.getProperty("blankqm.filepath"));
        QualityModelImport qmImport = new QualityModelImport(blankqmFilePath);
        QualityModel qmDescription = qmImport.importQualityModel();


        ITool gyrpeWrapper = new GrypeWrapper(prop.getProperty("github-token-path"), prop.getProperty("nvd-api-key-path"));
        //TODO ITool trivyWrapper = new TrivyWrapper(prop.getProperty("github-token-path"));
        Set<ITool> tools = Stream.of(gyrpeWrapper).collect(Collectors.toSet());


        Map<String, BigDecimal[]> thresholds = benchmarker.deriveThresholds(Paths.get(prop.getProperty("benchmark.repo")),
                qmDescription, tools, "");

       // ArrayList<Project> projects = benchmarker.analyzeProjects(Set<Path> dockerImages, QualityModel qmDescription, Set< ITool > tools)

    }
}

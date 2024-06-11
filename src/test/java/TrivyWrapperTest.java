import org.junit.BeforeClass;
import org.junit.Test;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.utility.PiqueProperties;
import tool.GrypeWrapper;
import tool.TrivyWrapper;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

public class TrivyWrapperTest {

    private static TrivyWrapper trivyWrapper;

    public TrivyWrapperTest(){

    }

    @BeforeClass
    public static void setup(){
        //load properties
        Properties prop = PiqueProperties.getProperties();
        trivyWrapper = new TrivyWrapper(prop.getProperty("github-token-path"), prop.getProperty("nvd-api-key-path"));//, prop.getProperty("nvd-api-key-path"));

        //load docker marshaller
        DockerMarshallerTest.init();
    }

    @Test
    public void runTrivy(){
        Path alpine3_15_output = trivyWrapper.analyze(Paths.get("alpine:3.15"));
        //maybe one or two tests to see if grype ran correctly

        Map<String, Diagnostic> results = trivyWrapper.parseAnalysis(alpine3_15_output);


        Diagnostic d = results.get("CWE-754 Diagnostic Trivy");
        // one finding coming from alpine:3.15
        assertEquals(1, d.getChildren().keySet().size());
        // check that the finding matches with our expectations (name + critical severity + value)
        Finding f = (Finding) d.getChild("CVE-2023-5678");
        assertEquals(7, f.getSeverity());
        assertEquals(new BigDecimal(7), f.getValue());

        //check the value is being aggregated correctly to diagnostic value
        assertEquals(new BigDecimal(7.0).stripTrailingZeros(), d.getValue().stripTrailingZeros());

    }
}

package tool;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import pique.analysis.ITool;
import pique.analysis.Tool;
import pique.model.Diagnostic;
import pique.model.Finding;
import pique.utility.PiqueProperties;
import utilities.helperFunctions;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

public class GrypeWrapper extends Tool implements ITool {
    private static final Logger LOGGER = LoggerFactory.getLogger(GrypeWrapper.class);
    private String githubTokenPath;
    private String nvdAPIKeyPath;

    public GrypeWrapper(String githubTokenPath) {
        super("Grype", null);
        this.githubTokenPath = githubTokenPath;
    }

    public GrypeWrapper(String githubTokenPath, String nvdAPIKeyPath) {
        super("Grype", null);
        this.githubTokenPath = githubTokenPath;
        this.nvdAPIKeyPath = nvdAPIKeyPath;
    }

    // Methods
    /**
     * @param projectLocation The name of the image to analyze, with format: "name:tag"
     * @return The path to the analysis results file
     */
    @Override
    public Path analyze(Path projectLocation) {
        //workaround because grype targets images, which are loaded by docker
        String imageName = projectLocation.toString();
        System.out.println("Analyzing "+ imageName + " with " + this.getName());
        String imageNameForDirectory = imageName.split(":")[0];
        //set up results dir
        String workingDirectoryPrefix = "";

        try {
            //read output dir from properties file. FIXME we need better properties import
            Properties prop = PiqueProperties.getProperties("src/main/resources/pique-properties.properties");
            Path resultsDir = Paths.get(prop.getProperty("results.directory"));

            workingDirectoryPrefix = resultsDir + "/tool-out/" + imageNameForDirectory + "/";
            Files.createDirectories(Paths.get(workingDirectoryPrefix));
        }catch(java.io.IOException e) {
            e.printStackTrace();
            LOGGER.debug("Error creating directory to save Grype tool results");
            System.out.println("Error creating directory to save Grype tool results");
        }
        File tempResults = new File(workingDirectoryPrefix + "grype-" + imageName + ".json");
        if (tempResults.exists()){
            LOGGER.info("Already ran Grype on: " + imageName + ", results stored in: " + tempResults.toString());
        }else {
            LOGGER.info("Have not run Grype on: " + imageName + ", running now and storing in:" +  tempResults.toString());
            tempResults.getParentFile().mkdirs();

            String[] cmd = {"grype",
                    imageName,
                    "--scope", "all-layers",
                    "-o", "json",
                    "--file", tempResults.toPath().toAbsolutePath().toString()};
            LOGGER.info(Arrays.toString(cmd));
            try {
                helperFunctions.getOutputFromProgram(cmd, LOGGER);
            } catch (IOException e) {
                LOGGER.error("Failed to run Grype");
                LOGGER.error(e.toString());
                e.printStackTrace();
            }
        }

        return tempResults.toPath();
    }

    /**
     * parses output of tool from analyze().
     *
     * @param toolResults location of the results, output by analyze()
     * @return A Map<String,Diagnostic> with findings from the tool attached. Returns null if tool failed to run.
     */
    @Override
    public Map<String, Diagnostic> parseAnalysis(Path toolResults) {
        System.out.println(this.getName() + " Parsing Analysis...");
        LOGGER.debug(this.getName() + " Parsing Analysis...");

        Map<String, Diagnostic> diagnostics = helperFunctions.initializeDiagnostics(this.getName());

        String results = "";

        try {
            results = helperFunctions.readFileContent(toolResults);
        } catch (IOException e) {
            LOGGER.info("No results to read from Grype.");
        }

        //TODO change these arraylists to the data structure Pairs. Later on when we initialize them as findings we key both arrays off one index
        ArrayList<String> cveList = new ArrayList<>();
        ArrayList<Integer> severityList = new ArrayList<>();

        try {
            JSONObject jsonResults = new JSONObject(results);
            JSONArray matches = jsonResults.optJSONArray("matches");

            // if vulnerabilities is null we had no findings, thus return
            if (matches == null) {
                return diagnostics;
            }

            for (int i = 0; i < matches.length(); i++) {
                JSONObject jsonFinding = ((JSONObject) matches.get(i)).optJSONObject("vulnerability");
                //Need to change this for this tool.
                String findingName = jsonFinding.get("id").toString();
                String findingSeverity = jsonFinding.get("severity").toString();
                severityList.add(this.severityToInt(findingSeverity));
                cveList.add(findingName);
            }

            // use nvd dump or not. NVD dump avoids timeout issues with NVD but DB dump load takes a while too....
            // Trying the raw NVD DB dump for now, might change later
//            String[] findingNames = helperFunctions.getCWEFromNVDDatabaseDump(cveList, this.githubTokenPath);
            String[] findingNames = helperFunctions.getCWEFromNVDAPIDirectly(cveList, this.githubTokenPath, this.nvdAPIKeyPath);

            for (int i = 0; i < findingNames.length; i++) {
                Diagnostic diag = diagnostics.get((findingNames[i] + " Diagnostic Grype"));
                if (diag != null) {
                    Finding finding = new Finding("",0,0,severityList.get(i));
                    finding.setName(cveList.get(i));
                    diag.setChild(finding);
                }else{
                    //this means that either it is unknown, mapped to a CWE outside of the expected results, or is not assigned a CWE
                    // We may want to treat this in another way in the future, but im ignoring it for now.
                    System.out.println("Vulnerability " + cveList.get(i) + " with CWE: " + findingNames[i] + "  outside of CWE-1000 was found. Ignoring this CVE.");
                    LOGGER.warn("Vulnerability " + cveList.get(i) + " with CWE: " + findingNames[i] + "  outside of CWE-1000 was found. Ignoring this CVE.");
                }
            }
        } catch (JSONException e) {
            LOGGER.warn("Unable to read results from Grype");
        }

        return diagnostics;
    }

    /**
     * Initializes the tool by installing it through python pip from the command line.
     */
    @Override
    public Path initialize(Path toolRoot) {
        final String[] cmd = {"grype", "version"};

        try {
            helperFunctions.getOutputFromProgram(cmd, LOGGER);
        } catch (IOException e) {
            e.printStackTrace();
            LOGGER.error("Failed to initialize " + this.getName());
            LOGGER.error(e.getStackTrace().toString());
        }

        return toolRoot;
    }


    //maps low-critical to numeric values based on the highest value for each range.
    private Integer severityToInt(String severity) {
        Integer severityInt = 1;
        switch(severity.toLowerCase()) {
            case "low": {
                severityInt = 4;
                break;
            }
            case "medium": {
                severityInt = 7;
                break;
            }
            case "high": {
                severityInt = 9;
                break;
            }
            case "critical": {
                severityInt = 10;
                break;
            }
        }

        return severityInt;
    }
}

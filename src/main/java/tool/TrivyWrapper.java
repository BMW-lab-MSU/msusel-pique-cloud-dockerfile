 /**
 * MIT License
 * Copyright (c) 2019 Montana State University Software Engineering Labs
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package tool;

 import exceptions.DataAccessException;
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
 import presentation.PiqueData;
 import utilities.HelperFunctions;

 import javax.ws.rs.HEAD;
 import java.io.File;
 import java.io.IOException;
 import java.nio.file.Files;
 import java.nio.file.Path;
 import java.nio.file.Paths;
 import java.util.*;

 /**
  * CODE TAKEN FROM PIQUE-BIN-DOCKER AND MODIFIED FOR PIQUE-SBOM-CONTENT and PIQUE-CLOUD-DOCKERFILE.
  * This tool wrapper will run and analyze the output of the tool.
  * When parsing the output of the tool, a command line call to run a Python script is made. This script is responsible for translating from
  * CVE number to the CWE it is categorized as by the NVD.
  * @author Derek Reimanis
  *
  */
 public class TrivyWrapper extends Tool implements ITool  {
     private static final Logger LOGGER = LoggerFactory.getLogger(TrivyWrapper.class);
     private PiqueData piqueData;

     public TrivyWrapper(PiqueData piqueData) {
         super("Trivy", null);
         this.piqueData = piqueData;
     }

     // Methods
         /**
          * @param projectLocation The path to a binary file for the desired solution of project to analyze
          * @return The path to the analysis results file
          */
         @Override
         public Path analyze(Path projectLocation) {
             String imageName = projectLocation.toString();
             LOGGER.info(this.getName() + "  Analyzing "+ imageName);
             System.out.println("Executing SAT " + this.getName() + " on image: " + imageName);
             LOGGER.debug("Executing SAT " + this.getName() + " on image: " + imageName);
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
                 LOGGER.debug("Error creating directory to save Trivy tool results");
                 System.out.println("Error creating directory to save Trivy tool results");
             }
             File tempResults = new File(workingDirectoryPrefix + "trivy-" + imageName + ".json");
             if (tempResults.exists()){
                 LOGGER.info("Already ran Trivy on: " + imageName + ", results stored in: " + tempResults.toString());
             }else {
                 LOGGER.info("Have not run Trivy on: "+ imageName + ", running now and storing in:" +  tempResults.toString());
                 System.out.println("Have not run Trivy on: "+ imageName + ", running now and storing in:" +  tempResults.toString());
                 tempResults.getParentFile().mkdirs();
                 //Unlike Grype, Trivy does not automatically download an image if it doesn't already exist.
                 //so, we need to download it.

                 //there is a bug here -- what is happening is that when we run this without mapping the docker daemon to the
                 //container this will be eventually be running in, the java-docker library does not have permissions to talk
                 //to the docker daemon on the system. I think we just need to assume that the user has the docker image they
                 //want to analyze already on their system. Commenting out for now..
                 //DockerMarshaller.downloadDockerImageFromDockerHub(imageName);

                 String[] cmd = {"trivy",
                         "--timeout", "30m",
                         "image",
                         "--format", "json",
                         "--quiet",
                         "--offline-scan", //try to get around https://github.com/aquasecurity/trivy/discussions/5420
                         "--output", tempResults.toPath().toAbsolutePath().toString(),
                         projectLocation.toString()};
                 LOGGER.info(Arrays.toString(cmd));
                 try {
                     HelperFunctions.getOutputFromProgram(cmd, LOGGER);
                 } catch (IOException e) {
                     LOGGER.error("Failed to run Trivy");
                     System.out.println("Failed to run Trivy");
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
             System.out.println("Parsing output from SAT " + this.getName());
             LOGGER.debug("Parsing output from SAT " + this.getName());

             Map<String, Diagnostic> diagnostics = HelperFunctions.initializeDiagnostics(this.getName());

             String results = "";

             try {
                 results = HelperFunctions.readFileContent(toolResults);
             } catch (IOException e) {
                 LOGGER.info("No results to read from Trivy.");
             }

             try {
                 JSONObject jsonResults = new JSONObject(results);
                 //do cwe fields exist?
                 JSONArray trivyResults = jsonResults.optJSONArray("Results");

                 // if the results field is null we had no findings, thus return
                 if (trivyResults == null) {
                     return diagnostics;
                 }

                 for (int i = 0; i < trivyResults.length(); i++) {
                     JSONArray jsonVulnerabilities = ((JSONObject) trivyResults.get(i)).optJSONArray("Vulnerabilities");
                     if (jsonVulnerabilities != null){
                         //apparently is null when no vulnerabilities are found.
                         for (int j = 0; j < jsonVulnerabilities.length(); j++) {
                             JSONObject jsonFinding = ((JSONObject) jsonVulnerabilities.get(j));
                             String vulnerabilityID = jsonFinding.getString("VulnerabilityID");
                             //trivy does not find GHSAs, so skip
                             //associated CWEs are Aqua's CWEs and NVDs CWEs
                             ArrayList<String> associatedCWEs = new ArrayList<>();
                             // get aqua CWEs
                             JSONArray jsonWeaknesses = jsonFinding.optJSONArray("CweIDs");
                             if (jsonWeaknesses != null) {
                                 for (int k = 0; k < jsonWeaknesses.length(); k++) {
                                     associatedCWEs.add(jsonWeaknesses.get(k).toString());
                                 }
                             }
                             // get NVD CWEs
                             //FIXME--- remove try catch block after checked exceptions changes
                             try {
                                 //do we have a cwe for this cve?
                                 associatedCWEs.addAll(piqueData.getCweName(vulnerabilityID));
                             }catch (DataAccessException e){
                                 LOGGER.error(vulnerabilityID + " has no NVD page, page likely reserved by a CNA. Skipping.");
                             }catch (Exception e){
                                 //catch all, remove after
                                 System.out.println("PIQUE data exception caught, most likely because the NVD isn't updated");
                                 LOGGER.error("PIQUE data exception caught, most likely because the NVD isn't updated");
                             }

                             // remove duplicate CWEs
                             Set<String> cweSet = new HashSet<>(associatedCWEs);
                             associatedCWEs.clear();
                             associatedCWEs.addAll(cweSet);
                             //regardless of cwes, continue with severity.
                             String vulnerabilitySeverity = jsonFinding.getString("Severity");
                             int severity = this.severityToInt(vulnerabilitySeverity);
                             int uniqueFindingCounter = 0;
                             for (int k = 0; k < associatedCWEs.size(); k++) {
                                 Diagnostic diag = diagnostics.get((associatedCWEs.get(k) + " Diagnostic Trivy"));
                                 if (diag == null) {
                                     //this means the vuln was mapped to a CWE outside the 1000 scope
                                     LOGGER.info("Vulnerability " + vulnerabilityID + " with CWE: " + associatedCWEs.get(k) + " mapped to CWE-outside-1000");
                                     diag = diagnostics.get("CWE-outside-1000 Diagnostic Trivy");
                                 }
                                 //found a cwe node for this in the model definition, creating a finding for it and adding.
                                 LOGGER.info("Found " + associatedCWEs.get(k) + " in the model definition for our " + vulnerabilityID +  " and adding to diag node: " + diag.getName());
                                 Finding finding = new Finding("" + uniqueFindingCounter,0,0, this.severityToInt(vulnerabilitySeverity));
                                 diag.setChild(finding);
                                 uniqueFindingCounter++;
                             }
                         }
                     }
                 }

             } catch (JSONException e) {
                 LOGGER.warn("Unable to read results from Trivy JSON output");
             }

             return diagnostics;
         }

         /**
          * Initializes the tool by installing it through python pip from the command line.
          */
         @Override
         public Path initialize(Path toolRoot) {
             final String[] cmd = {"trivy", "version"};

             try {
                 HelperFunctions.getOutputFromProgram(cmd, LOGGER);
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
                     severityInt = 1;
                     break;
                 }
                 case "medium": {
                     severityInt = 3;
                     break;
                 }
                 case "high": {
                     severityInt = 6;
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

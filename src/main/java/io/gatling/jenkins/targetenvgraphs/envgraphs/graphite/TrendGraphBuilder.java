/**
 * Copyright 2011-2012 eBusiness Information, Groupe Excilys (www.excilys.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gatling.jenkins.targetenvgraphs.envgraphs.graphite;

import io.gatling.jenkins.AssertionData;
import io.gatling.jenkins.targetenvgraphs.Brand;
import io.gatling.jenkins.targetenvgraphs.ProjectNameParser;
import org.apache.commons.lang.text.StrSubstitutor;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrendGraphBuilder {

    protected static final String ROOT_GRAPHITE_URL =
            "http://tre-stats.internal.shutterfly.com/render?";
    protected static final String KO_TARGET =
            "target=alias(color(secondYAxis(load.summary.${env}.${simName}.${reqName}.ko.percent)%2C%22red%22)" +
                    "%2C%22percent%20KOs%22)";
    protected static final String PERFORMANCE_STAT_TARGET =
            "target=alias(load.summary.${env}.${simName}.${reqName}.all.${assertName}%2C%22${assertDescr}%22)";
    protected static final String PERFORMANCE_ASSERT_THRESHOLD_TARGET =
            "target=alias(load.summary.${env}.${simName}.${reqName}.all.expected.${assertName}%2C%22performance+assert+" +
                    "threshold%22)";
    protected static final String RELEASE_BRANCH_TARGET =
            "target=alias(color(lineWidth(drawAsInfinite(integral(sfly.releng.branch.*))%2C1)%2C%22yellow%22)%2C%22" +
                    "Release%20Branch%20Created%22)";
    protected static final String RENDER_OPTIONS =
            "width=586&height=308&lineMode=connected&from=${fromDateTime}&title=${reqName}+-+${assertDescr}&vtitle=" +
                    "${performanceMetricLabel}&vtitleRight=Percentage_KOs&bgcolor=FFFFFF&fgcolor=000000&yMaxRight=100" +
                    "&yMinRight=0&hideLegend=false&uniqueLegend=true";
    protected static final String PERFORMANCE_METRIC_LABEL_THROUGHPUT = "Requests_per_second";
    public static final String PERFORMANCE_METRIC_LABEL_RESPONSE_TIME = "Response_Time_in_ms";
    private SimpleDateFormat graphiteDateFormat = new SimpleDateFormat("HH:mm_yyyyMMdd");
    private static final Logger logger = Logger.getLogger(TrendGraphBuilder.class.getName());

    public enum GRAPHITE_ASSERT_TYPE {
		percentiles50,
		percentiles80,
        percentiles95,
        percentiles99,
        mean,
        min,
        max,
        stddev,
        throughput,
        ko;

        public static GRAPHITE_ASSERT_TYPE fromGatlingAssertType(String assertionType){
            if(assertionType.contains("50th")){
                return percentiles50;
            } else if(assertionType.contains("80th")){
				return percentiles80;
			} else if(assertionType.contains("95th")){
				return percentiles95;
			} else if(assertionType.contains("99th")){
                return percentiles99;
            } else if(assertionType.contains("requests per second")){
				return throughput;
			} else if(assertionType.contains("mean")){
                return mean;
            } else if(assertionType.contains("KO")){
                return ko;
            } else if(assertionType.contains("min")){
                return min;
            } else if(assertionType.contains("max")){
                return max;
            } else if(assertionType.contains("standard deviation")){
                return stddev;
			}
            throw new IllegalArgumentException("Unexpected gatling type: " + assertionType);
        }
    }

    public String getGraphiteUrlForAssertion(Date fromDate, AssertionData assertionData) {
        final Map<String, String> values = buildValuesForTemplate(fromDate, assertionData);
        return fillInTemplate(getRootUrl() +
                getKOTarget() +
                "&" + getPerformanceStatTarget() +
                "&" + getPerformanceAssertThresholdTarget() +
                "&" + getReleaseBranchTarget() +
                "&" + getRenderOptions(), values);
    }

    public String modifyGraphiteUrlToSpanPreviousDays(String url, String dayOffset) {
        try {
            int numDays = Integer.parseInt(dayOffset);
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, numDays*-1);
            Date fromDate =  cal.getTime();

            String formattedFromDate = URLEncoder.encode(convertDateToGraphiteFormat(fromDate), "UTF-8");

            int fromParamIndex = url.indexOf("&from=");
            int firstParamAfterFromIndex = url.indexOf("&", fromParamIndex+1);

            String urlBeforeFromParam = url.substring(0, fromParamIndex);
            String urlAfterFromParam = url.substring(firstParamAfterFromIndex);

            return urlBeforeFromParam + "&from=" + formattedFromDate + urlAfterFromParam;
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to modify graphite URL with from and until dates. Return default url", e);
            return url;
        }
    }

    protected String getRootUrl() {
        return ROOT_GRAPHITE_URL;
    }

    protected String getKOTarget() {
        return KO_TARGET;
    }

    protected String getPerformanceStatTarget() {
        return PERFORMANCE_STAT_TARGET;
    }

    protected String getPerformanceAssertThresholdTarget() {
        return PERFORMANCE_ASSERT_THRESHOLD_TARGET;

    }

    protected String getReleaseBranchTarget() {
        return RELEASE_BRANCH_TARGET;
    }

    protected String getRenderOptions() {
        return RENDER_OPTIONS;
    }

    protected String fillInTemplate(String template,  Map<String,String> values){
        if(values == null)
            return null;
        StrSubstitutor sub = new StrSubstitutor(values);
        return sub.replace(template);
    }

    protected Map<String,String> buildValuesForTemplate(
            Date fromDate, AssertionData assertionData){
        try{
            Map<String,String> values = new HashMap<String,String>();
            String env = getEnvFromProjectName(assertionData.projectName);
            if(env != null){
                GRAPHITE_ASSERT_TYPE graphiteAssertionType =
                        convertAssertionTypeFromGatlingToGraphite(assertionData.assertionType);
                String performanceMetricLabel = PERFORMANCE_METRIC_LABEL_RESPONSE_TIME;
                if(isPerformanceAssert(graphiteAssertionType)){
                    if(graphiteAssertionType == GRAPHITE_ASSERT_TYPE.throughput){
                        performanceMetricLabel = PERFORMANCE_METRIC_LABEL_THROUGHPUT;
                    }
					String[] sim_split = assertionData.simulationName.split("\\.");
					String simulationTrimName = sim_split[sim_split.length - 1];
                    setUrlEncodedValue(values, "env", env);
                    setUrlEncodedValue(values, "simName",
                            graphiteSanitize(simulationTrimName.toLowerCase()));
                    setUrlEncodedValue(values, "reqName",
                            graphiteSanitize(gatlingRequestNameToGraphiteRequestName(
                            assertionData.requestName)));
                    setUrlEncodedValue(values, "assertName", graphiteAssertionType.name());
                    setUrlEncodedValue(values, "assertDescr", assertionData.assertionType);
                    setUrlEncodedValue(values, "projName", assertionData.projectName);
                    setUrlEncodedValue(values, "performanceMetricLabel", performanceMetricLabel);
                    setUrlEncodedValue(values, "fromDateTime", convertDateToGraphiteFormat(fromDate));
                    if(graphiteAssertionType == GRAPHITE_ASSERT_TYPE.throughput)
                        setUrlEncodedValue(values, "performanceStatSummarizeMethod", "min");
                    else
                        setUrlEncodedValue(values, "performanceStatSummarizeMethod", "max");
                    return values;
                }
            }
        }catch(UnsupportedEncodingException ex){
            throw new RuntimeException(ex);
        }
        return null;
    }

    private void setUrlEncodedValue(
            Map<String, String> values, String valueName,
            String value) throws UnsupportedEncodingException {
        values.put(valueName, URLEncoder.encode(value, "UTF-8"));
    }

    private boolean isPerformanceAssert(GRAPHITE_ASSERT_TYPE graphiteAssertionType) {
        return (graphiteAssertionType != null) &&
                (graphiteAssertionType != GRAPHITE_ASSERT_TYPE.ko);
    }

    private String convertDateToGraphiteFormat(Date inputDate) {
        try {
            return graphiteDateFormat.format(inputDate);
        } catch (Exception e) {
            logger.log(Level.WARNING,
                    "Failed to find date/time of oldest build for project.  " +
                            "Defaulting range to -1 month", e);
            return "-1months";
        }
    }

    private String getEnvFromProjectName(String projectName) {
        ProjectNameParser projectNameParser = new ProjectNameParser(projectName);
        if(projectNameParser.getBrand() == Brand.SHUTTERFLY){
            return projectNameParser.getEnv();
        } else {
            return projectNameParser.getBrand().name + "-" + projectNameParser.getEnv();
        }
    }


    private String gatlingRequestNameToGraphiteRequestName(String requestName) {
        if(requestName.compareTo("Global") == 0){
            return "Global_Information";
        }
        return requestName;
    }

    private String graphiteSanitize(String data) {
        return data.replaceAll("[^\\w\\.\\-_]", "_");
    }

    private GRAPHITE_ASSERT_TYPE convertAssertionTypeFromGatlingToGraphite(String assertionType) {
        if(assertionType == null)
            throw new RuntimeException("Invalid input assertType is null");
        return GRAPHITE_ASSERT_TYPE.fromGatlingAssertType(assertionType);
    }

}

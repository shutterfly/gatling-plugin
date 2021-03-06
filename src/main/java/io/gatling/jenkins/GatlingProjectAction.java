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
package io.gatling.jenkins;

import static io.gatling.jenkins.PluginConstants.*;
import hudson.model.*;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import io.gatling.jenkins.chart.Graph;
import io.gatling.jenkins.targetenvgraphs.envgraphs.graphite.TrendGraphBuilder;

import org.apache.commons.lang.builder.ToStringBuilder;



public class GatlingProjectAction implements Action {

    private final Job<?, ?> job;
    protected TrendGraphBuilder trendGraphBuilder;
    private static final Logger logger = Logger.getLogger(GatlingProjectAction.class.getName());

    public GatlingProjectAction(Job<?, ?> job) {
		this.job = job;
        this.trendGraphBuilder = new TrendGraphBuilder();
	}

	public String getIconFileName() {
		return ICON_URL;
	}

	public String getDisplayName() {
		return DISPLAY_NAME_REPORTS;
	}

	public String getUrlName() {
		return URL_NAME;
	}

    public Job<?, ?> getJob() {return job;}

	@SuppressWarnings("UnusedDeclaration")
    public boolean isVisible() {
        for (Run<?, ?> build : getJob().getBuilds()) {
            GatlingBuildAction gatlingBuildAction = build.getAction(GatlingBuildAction.class);
            if (gatlingBuildAction != null) {
                return true;
            }
        }
        return false;
    }

	@SuppressWarnings("UnusedDeclaration")
    public Graph<Long> getDashboardGraph() {
		return new Graph<Long>(job, MAX_BUILDS_TO_DISPLAY_DASHBOARD) {
			@Override
			public Long getValue(RequestReport requestReport) {
				return requestReport.getMeanResponseTime().getTotal();
			}
		};
	}

    @SuppressWarnings("UnusedDeclaration")
	public Graph<Long> getMeanResponseTimeGraph() {
		return new Graph<Long>(job, MAX_BUILDS_TO_DISPLAY) {
			@Override
			public Long getValue(RequestReport requestReport) {
				return requestReport.getMeanResponseTime().getTotal();
			}
		};
	}

    @SuppressWarnings("UnusedDeclaration")
	public Graph<Long> getPercentileResponseTimeGraph() {
		return new Graph<Long>(job, MAX_BUILDS_TO_DISPLAY) {
			@Override
			public Long getValue(RequestReport requestReport) {
				return requestReport.getPercentiles95().getTotal();
			}
		};
	}

    @SuppressWarnings("UnusedDeclaration")
	public Graph<Long> getRequestKOPercentageGraph() {
		return new Graph<Long>(job, MAX_BUILDS_TO_DISPLAY) {
			@Override
			public Long getValue(RequestReport requestReport) {
				return Math.round(requestReport.getNumberOfRequests().getKO() * 100.0 / requestReport.getNumberOfRequests().getTotal());
			}
		};
	}

    @SuppressWarnings("UnusedDeclaration")
    public Map<Run<?, ?>, List<String>> getReports() {
        Map<Run<?, ?>, List<String>> reports = new LinkedHashMap<Run<?, ?>, List<String>>();
        for (Run<?, ?> build : job.getBuilds()) {
            GatlingBuildAction action = build.getAction(GatlingBuildAction.class);
            if (action != null) {
                List<String> simNames = new ArrayList<String>();
                for (BuildSimulation sim : action.getSimulations()) {
                    simNames.add(sim.getSimulationName());
                }
                reports.put(build, simNames);
            }
        }
        return reports;
    }

    public String getReportURL(int build, String simName) {
        return new StringBuilder().append(build).append("/").append(URL_NAME).append("/report/").append(simName).toString();
    }

    public List<String> getGraphiteGraphUrlsForBuildHistory() {
        for (Run<?, ?> build : job.getBuilds()) {
            List<String> retVal = getGraphiteUrlsForBuild(build);
            if (retVal != null)
                return retVal;
        }
        return new ArrayList<String>();
    }

    private List<String> getGraphiteUrlsForBuild(Run<?, ?> build) {
        List<String> retVal = new ArrayList<String>();
        GatlingBuildAction gatlingBuildAction = build.getAction(GatlingBuildAction.class);
        if (gatlingBuildAction != null) {
           List<AssertionData> assertionDataList = gatlingBuildAction.getAssertionDataList();
           if(assertionDataList != null){
               Run<?, ?> firstBuild = job.getFirstBuild();
               if(firstBuild == null){
                   return retVal;
               }
               Calendar timestamp = firstBuild.getTimestamp();
               Date time = timestamp.getTime();
               for(AssertionData assertionData : assertionDataList){
                   appendAssertUrl(retVal, time, assertionData);
               }
               // only build urls from the most recent build that has assertions -Vito
               return retVal;
           }
        }
        return null;
    }

    private void appendAssertUrl(List<String> retVal, Date time, AssertionData assertionData) {
        try {
            String url = generateGraphiteUrl(time, assertionData);
            if (url != null) {
                retVal.add(url);
            }
        }catch(Exception ex){
            String projectName = "Unknown";
            if(job != null)
                projectName = job.getName();
            final String assertionDataString =
                    ToStringBuilder.reflectionToString(assertionData);
            logger.log(
                    Level.WARNING,
                    "Failed to generate url for assertion data\n" +
                        "Project Name: " +  projectName + "\n" +
                        assertionDataString,
                    ex);
        }
    }

    private String generateGraphiteUrl(Date time, AssertionData assertionData) {
        return trendGraphBuilder.getGraphiteUrlForAssertion(time, assertionData);
    }

    public String modifyGraphiteUrlForPastDays(String url, String daysOffset) {
        try {
            return trendGraphBuilder.modifyGraphiteUrlToSpanPreviousDays(url, daysOffset);
        } catch(Exception e) {
            Logger.getLogger(this.getClass().getName()).log(Level.WARNING,
                    "Failed to modify graphite url with from/until dates.  URL: " + url + "\nOffset: " + daysOffset, e);
            return url;
        }
    }
}

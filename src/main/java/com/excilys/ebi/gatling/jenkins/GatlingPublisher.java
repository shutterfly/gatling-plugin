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
package com.excilys.ebi.gatling.jenkins;

import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.XmlFile;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Action;
import hudson.model.BuildListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import org.kohsuke.stapler.DataBoundConstructor;

import java.io.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.excilys.ebi.gatling.jenkins.PluginConstants.ICON_URL;
import static com.excilys.ebi.gatling.jenkins.PluginConstants.GRAPHITE_ICON;
import static com.excilys.ebi.gatling.jenkins.PluginConstants.DISPLAY_NAME_SOURCE;
import static com.excilys.ebi.gatling.jenkins.PluginConstants.DISPLAY_SYSTEM_INFO;


public class GatlingPublisher extends Recorder {

	private final Boolean enabled;
	private AbstractProject<?, ?> project;
    private AbstractBuild<?, ?> build;
	private PrintStream logger;


	@DataBoundConstructor
	public GatlingPublisher(Boolean enabled) {
		this.enabled = enabled;
	}

	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) throws InterruptedException, IOException {
		this.build = build;
		logger = listener.getLogger();
		if (enabled == null) {
			logger.println("Cannot check Gatling simulation tracking status, reports won't be archived.");
			logger.println("Please make sure simulation tracking is enabled in your build configuration !");
			return true;
		}
		if (!enabled) {
			logger.println("Simulation tracking disabled, reports were not archived.");
			return true;
		}

		logger.println("Archiving Gatling reports...");
        List<BuildSimulation> sims = saveFullReports(build.getWorkspace(), build.getRootDir());
        if (sims.size() == 0) {
			logger.println("No newer Gatling reports to archive.");
			return true;
		}

        List<AssertionData> assertionDataList = readAssertionData(sims);
        GatlingBuildAction action = new GatlingBuildAction(build, sims, assertionDataList);

        build.addAction(action);

		List<SimulationSourceAction> simSourceActions = generateSimulationSourceActionsFromGatlingBuildAction(action, false);
		for (SimulationSourceAction act : simSourceActions){
			build.addAction(act);
		}

        List<GraphiteAction> graphiteActions = generateGraphiteActionFromGatlingBuildAction(action, false);
        for(GraphiteAction graphiteAction : graphiteActions) {
            build.addAction(graphiteAction);
        }

		logger.println("Setting Build Description...");
		try{
			build.setDescription(this.generateBuildDescriptionFromAssertionData(assertionDataList));
		}catch(Exception e){
			logger.println("ERROR in Setting Build Description" + e);
		}

        return true;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public BuildStepMonitor getRequiredMonitorService() {
		return BuildStepMonitor.BUILD;
	}

	@Override
	public final Collection<? extends Action> getProjectActions(AbstractProject<?,?> project){
		this.project = project;
		List<Action> actions = new ArrayList<Action>();
		actions.add(new GatlingProjectAction(project));
		try{
			GatlingBuildAction lastBuildAct = getLastBuildGatlingBuildAction(project);
			List<SimulationSourceAction> simSourceActions = generateSimulationSourceActionsFromGatlingBuildAction(lastBuildAct, true);
			for (SimulationSourceAction act : simSourceActions){
				actions.add(act);
			}
            List<GraphiteAction> graphiteActions = generateGraphiteActionFromGatlingBuildAction(lastBuildAct, true);
            for(GraphiteAction graphiteAction : graphiteActions) {
                actions.add(graphiteAction);
            }
		}catch (Exception e){}
		return actions;
	}

	private GatlingBuildAction getLastBuildGatlingBuildAction(AbstractProject<?,?> project){
		AbstractBuild<?,?> lastbuild = project.getLastCompletedBuild();
		GatlingBuildAction lastBuildAct = lastbuild.getAction(GatlingBuildAction.class);
		return lastBuildAct;
	}

	private List<SimulationSourceAction> generateSimulationSourceActionsFromGatlingBuildAction(GatlingBuildAction buildaction, Boolean isProject){
		List<SimulationSourceAction> sourceactions = new ArrayList<SimulationSourceAction>();
		String icon = ICON_URL;
		String url = "";
		for (BuildSimulation sim : buildaction.getSimulations()){
			if (isProject){
				Integer buildActionNum = buildaction.getBuild().getNumber();
				url = buildaction.getSimulationClassSourceURL(buildActionNum,sim.getSimulationName());
			}else{
				url = buildaction.getSimulationClassSourceURL(sim.getSimulationName());
			}
			String text = DISPLAY_NAME_SOURCE;
			SimulationSourceAction sourceaction = new SimulationSourceAction(url,text,icon);
			sourceactions.add(sourceaction);
		}
		return sourceactions;
	}

    private List<GraphiteAction> generateGraphiteActionFromGatlingBuildAction(GatlingBuildAction buildAction, Boolean isProject){
        List<GraphiteAction> sourceActions = new ArrayList<GraphiteAction>();
        String icon = GRAPHITE_ICON;
        String url = "";
        for (BuildSimulation sim : buildAction.getSimulations()){
            if (isProject){
                Integer buildActionNum = buildAction.getBuild().getNumber();
                url = buildAction.getGraphiteURL(buildActionNum,sim.getSimulationName());
            }else{
                url = buildAction.getGraphiteURL(sim.getSimulationName());
            }
            String text = DISPLAY_SYSTEM_INFO;
            GraphiteAction graphiteAction = new GraphiteAction(url,text,icon, buildAction.getBuild());
            sourceActions.add(graphiteAction);
        }
        return sourceActions;
	}



	public String getShortBuildDescription(AssertionData assertionData){
		StringBuffer description = new StringBuffer();
		String originalAssertionType = assertionData.assertionType;
		String convertAssertionType = "";
		String comparionSymbol = "";
		if(originalAssertionType.contains("95th")){
			convertAssertionType = "95th";
		}if(originalAssertionType.contains("99th")){
			convertAssertionType = "99th";
		}else if(originalAssertionType.contains("mean")){
			convertAssertionType = "mean";
		}else if(originalAssertionType.contains("KO")){
			convertAssertionType = "KO%"; // not a performance assert
		}else if(originalAssertionType.contains("min")){
			convertAssertionType = "min";
		}else if(originalAssertionType.contains("max")){
			convertAssertionType = "max";
		}else if(originalAssertionType.contains("standard deviation")){
			convertAssertionType = "stddev";
		}else if(originalAssertionType.contains("requests per second")){
			convertAssertionType = "req/s";
		}

		if(assertionData.message.contains("is greater than")){
			comparionSymbol = ">";
		}else if(assertionData.message.contains("is less than")){
			comparionSymbol = "<";
		}

		if (!convertAssertionType.isEmpty() && !comparionSymbol.isEmpty()){
			String requestNameWithNonBreakingSpace = assertionData.requestName.replace(" ","&nbsp;");
			description.append(requestNameWithNonBreakingSpace + "&nbsp;" + convertAssertionType + "=" + assertionData.actualValue + ",&nbsp;expect" + comparionSymbol + assertionData.expectedValue +";<br>");
		}else{
			String messageWithNonBreakingSpace = assertionData.message.replace(" ","&nbsp;");
			description.append(messageWithNonBreakingSpace + ":" + assertionData.status + "-Actual&nbsp;Value:"  + assertionData.actualValue + ";<br>");
		}

		return description.toString();
	}

	public String generateBuildDescriptionFromAssertionData(List<AssertionData> assertionDataList){
		StringBuffer description = new StringBuffer();
		String conclusion = "";
		Integer kocount = 0;
		Integer falsecount = 0;

        for( AssertionData assertionData : assertionDataList){
			if (assertionData.status.contains("false")){
				falsecount = falsecount + 1;
				if (assertionData.assertionType.contains("KO")) {
					kocount = kocount + 1;
				}
				description.append(getShortBuildDescription(assertionData));
			}
		}

		if (falsecount != 0){
			if (kocount == falsecount){
				conclusion = "<b>KO</b>";
			}else if (kocount == 0){
				conclusion = "<b>PERFORMANCE</b>";
			}else{
				conclusion = "<b>KO AND PERFORMANCE</b>";
			}
			return conclusion + "<br>" + description.toString();
		}else{
			return description.toString();
		}

	}

    private List<AssertionData> readAssertionData(List<BuildSimulation> sims) throws IOException, InterruptedException {

        List<AssertionData> assertionList = new ArrayList<AssertionData>();

        for (BuildSimulation sim : sims){
            FilePath workspace = sim.getSimulationDirectory();
            FilePath[] files = workspace.list("**/assertion.tsv");

            if (files.length == 0) {
                throw new IllegalArgumentException("Could not find a Gatling report in results folder.");
            }

            for (FilePath filepath : files) {
                File file = new File(filepath.getRemote());
                BufferedReader br = new BufferedReader(new FileReader(file));
                br.readLine();
                String line;
                while ((line = br.readLine()) != null) {
                    String[] values = line.split("\\t");
                    AssertionData assertionData = new AssertionData();

                    assertionData.projectName = project.getName();
                    assertionData.simulationName = file.getParentFile().getName().split("-")[0];
                    assertionData.scenerioName = values[0];
                    assertionData.requestName = values[1];
                    assertionData.message = values[2];
                    assertionData.assertionType = values[3];
                    assertionData.actualValue = values[4];
                    assertionData.expectedValue = values[5];
                    assertionData.status = values[6];
                    assertionList.add(assertionData);
                }
            }
        }

        return assertionList;
    }

	public String hasMatchSimulationClass(String input, Pattern pattern){
		String line = input.replace("&apos;","");
		String rs = "";

		Matcher matcher = pattern.matcher(line);
		if (matcher.find()) {
			rs = matcher.group(1);
		}
		return rs;
	}

	private String getSimulationClassFromMavenCommand() throws IOException {
		String result = "";
		XmlFile configfile = project.getConfigFile();
		Pattern pattern = Pattern.compile(".*-Dgatling\\.simulationClass=([a-zA-Z0-9\\.]+).*");
		String line = "";
		Reader configReader = configfile.readRaw();
		BufferedReader br = new BufferedReader(configReader);
		line = br.readLine();
		while (line != null) {
			result = hasMatchSimulationClass(line,pattern);
			if (!result.isEmpty()){
				break;
			}
			line = br.readLine();
		}
		br.close();
		return result;
	}

	private void saveSimulationSourceClass(FilePath workspace, FilePath reportDirectory) throws IOException, InterruptedException {
		String simSourceClass = getSimulationClassFromMavenCommand();
		String simSourceClassPath = "**/"+getSimulationSourceClass(simSourceClass);
		FilePath[] simSourceClassFiles = workspace.list(simSourceClassPath);

		if (simSourceClassFiles.length == 0) {
			throw new IllegalArgumentException("Could not find a simulation source class report in workspace.");
		}

		for (FilePath file : simSourceClassFiles) {
			logger.println("Adding '" + file.getName() + "' to the Report Directory...");
			file.getParent().copyRecursiveTo(file.getName(),reportDirectory);
		}
	}

	public String getSimulationSourceClass(String simulationClass){
		String classpath = simulationClass.replace(".", "/")+".scala";
		return classpath;
	}

    private List<BuildSimulation> saveFullReports(FilePath workspace, File rootDir) throws IOException, InterruptedException {
		FilePath[] statsFiles = workspace.list("**/global_stats.json");

		List<FilePath> reportFolders = new ArrayList<FilePath>();

		if (statsFiles.length == 0) {
			throw new IllegalArgumentException("Could not find a Gatling report in results folder.");
		}

		// Get reports folders for all "global_stats.json" found
		for (FilePath file : statsFiles) {
			reportFolders.add(file.getParent().getParent());
		}

        List<FilePath> reportsToArchive = selectReports(reportFolders);


        List<BuildSimulation> simsToArchive = new ArrayList<BuildSimulation>();

		// If the most recent report has already been archived, there's nothing else to do
        if (reportsToArchive.size() == 0) {
			return simsToArchive;
		}

        File allSimulationsDirectory = new File(rootDir, "simulations");
        if (!allSimulationsDirectory.exists())
            allSimulationsDirectory.mkdir();

        for (FilePath reportToArchive : reportsToArchive) {
            String name = reportToArchive.getName();
            int dashIndex = name.lastIndexOf('-');
            String simulation = name.substring(0, dashIndex);
            File simulationDirectory = new File(allSimulationsDirectory, name);
            simulationDirectory.mkdir();

            FilePath reportDirectory = new FilePath(simulationDirectory);

			reportToArchive.copyRecursiveTo(reportDirectory);

			try{
				saveSimulationSourceClass(workspace,reportDirectory);
			}catch(Exception e){
				logger.println("ERROR in archiving simulation source code: " + e);
			}

            SimulationReport report = new SimulationReport(reportDirectory, simulation);
            report.readStatsFile();
            BuildSimulation sim = new BuildSimulation(simulation, report.getGlobalReport(), reportDirectory);

            simsToArchive.add(sim);
        }


		return simsToArchive;
	}

	private List<FilePath> selectReports(List<FilePath> reportFolders) throws InterruptedException, IOException {
        long buildStartTime = build.getStartTimeInMillis();
        List<FilePath> reportsFromThisBuild = new ArrayList<FilePath>();
		for (FilePath reportFolder : reportFolders) {
            long reportLastMod = reportFolder.lastModified();
            if (reportLastMod > buildStartTime) {
                logger.println("Adding report '" + reportFolder.getName() + "'");
                reportsFromThisBuild.add(reportFolder);
            }
		}
        return reportsFromThisBuild;
	}

	@Extension
	public static class DescriptorImpl extends BuildStepDescriptor<Publisher> {

		@Override
		public boolean isApplicable(Class<? extends AbstractProject> aClass) {
			return true;
		}

		@Override
		public String getDisplayName() {
			return Messages.Title();
		}
	}


}

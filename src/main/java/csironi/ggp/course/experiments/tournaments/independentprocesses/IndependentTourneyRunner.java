package csironi.ggp.course.experiments.tournaments.independentprocesses;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.ThreadContext;
import org.ggp.base.player.gamer.statemachine.ConfigurableStateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.reflection.ProjectSearcher;
import org.ggp.base.util.statemachine.structure.explicit.ExplicitRole;

import csironi.ggp.course.experiments.tournaments.Combinator;
import csironi.ggp.course.experiments.tournaments.ExternalGamerAvailabilityManager;

/**
 * This class takes care of performing a tourney for the given game.
 * For the given game it computes all the possible combinations in which the given
 * gamer types can be assigned (with no exclusion of any of the types) to the roles
 * in the game and performs the specified amount of matches for each combination,
 * with the given settings.
 *
 * NOTE: for now it works only if #gamerTypes <= #gameRoles
 * @author C.Sironi
 *
 */
public class IndependentTourneyRunner {

	static{
		System.setProperty("isThreadContextMapInheritable", "true");
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {

		//System.out.println("!!" + ProjectSearcher.INTERNAL_PROPNET_GAMERS.getConcreteClasses().size());

		if(args.length != 1){
			System.out.println("Impossible to start experiment! Wrong number of inputs!");
			System.out.println("Specify the name of the file with the settings of the experiment!");
			return;
		}

		System.out.println("Trying to start experiment.");

		File propertiesFile = new File(args[0]);

		String tourneyName;
		// List of keys of all the games for the tourney.
		Set<String> gameKeys = new HashSet<String>();
		int startClock;
		int playClock;
		long pnCreationTime;
		// Number of parallel player threads that should run at the same time. NOTE that this requirement is loosely satisfied.
		// The actual number of player threads that will be running will be the closest feasible number of threads that allows
		// to run matches with the given number of roles for the game.
		int numParallelPlayers;
		// Minimum number of total games that a player should play (i.e. min num of samples for each player used to compute
		// the win percentage).
		int matchesPerGamerType;
		// Num matches that a match runner should run sequentially using the same player
		// (so that the players can "remember" previous games if the are capable of it).
		int numSequentialMatches;
		// List (without repetitions) of gamer types that we want to include in the experiment. Each gamer, if part of the code base
		// (i.e. internal gamer), must be specified with the exact name of the class that implements such gamer. If the gamer is a
		// subclass of the ConfigurableStateMachineGamer it must be specified with the exact name of the class that implements it
		// AND the name of the file in which its settings are specified, separated by "-" (e.g. MctsGamer-GraveDuct.properties, or
		// MctsGamer-Duct.properties). NOTE that the specified .properties file must be in the folder with the path
		// gamersSettingsFolderPath specified in the GamerConfiguration class. If one of the gamers is external (i.e. a gamer running
		// on its own, like CadiaPlayer, Sancho, etc...), specify its name and a list of all pairs (host-ports) on which the external
		// player is running (e.g. Cadia-127.0.0.1-9147-127.0.0.1-9148-127.0.0.1-9149-127.0.0.1-9150). Note that for a game, if there
		// are x roles in the game and y matches are running in parallel at least (x-1)*y instances of the external player must be
		// running at the same time. This means that at least (x-1)*y pairs (host,ports) must be specified, where x is the max number
		// of roles that a game in the tourney has.
		String[] theGamersTypesString;
		int runNumber;

		// Set to true when the tourney is meant to run once (not multiple times in parallel), but we might want to continue it in the
		// future adding further samples. In this case this method will make sure to keep track of the number of times the tourney has
		// been run (i.e. increasing the runNumber property in the .properties file), and will also check that the folder where stats
		// and logs are saved doesn't exist before the first run of the tourney, but does already exist for subsequent runs.
		// When set to false, it will mean that the tourney might be run multiple times in parallel, so we will still get a runNumber
		// greater than 0 even if we don't have a previously created folder of stats for the tourney. The different runs will be performed
		// in parallel and merged together later.
		boolean continueOldExperiment = false;
		// True if we want to log only essential files, false otherwise (false by default if not specified otherwise)
		boolean logOnlyEssentialFiles = false;
		// True if we want to give to external players unlimited time to respond to start and play requests (i.e. we don't check if they
		// exceeded the timeout when answering)
		boolean unlimitedTimeForExternal = false;
		// True if we want to repeat an old experiment that did not finish, thus we have some matches that ran and some that still have to run.
		boolean finishInterruptedExperiment = false;

		// Map that for each external gamer contains the manager of available addresses (IP+port) on which the external gamer
		// is listening for connections.
		Map<String,ExternalGamerAvailabilityManager> externalGamersManagers = new HashMap<String,ExternalGamerAvailabilityManager>();

		// The current time is used to distinguish multiple separate runs of the same experiment.
		// If I want to continue a previous experiment, all tourneys with the ID saved in the properties
		// will be continued. Whenever I want to run the experiment from the beginning a new ID will be
		// associated to the run so that whenever I want to continue such experiment the tourneys of the
		// other experiment with the same name won't be continued.
		Long timeID;

		FileReader reader;

		GameRepository gameRepo = GameRepository.getDefaultRepository();
//    	GameRepository gameRepo = new ManualUpdateLocalGameRepository("/home/iy353734/GAMEREPOS");
//		GameRepository gameRepo = new ManualUpdateLocalGameRepository(GamerConfiguration.defaultLocalGameRepositoryFolderPath + "/" + GamerConfiguration./*defaultStanfordRepo*/defaultGGPBaseRepo);

		Game game;

		String[] gamerTypes;
		String[] gamerSettings;

		try {
			reader = new FileReader(propertiesFile);

			Properties props = new Properties();

			// load the properties file:
			props.load(reader);

			reader.close();

			tourneyName = props.getProperty("tourneyName");

			String[] gameKeysStrings = props.getProperty("gameKeys").split(";");
			// Remove duplicate game keys and check if all games are available in the repository.
			// I check here if all games are in the repository because if even one is missing I
			// don't want the experiment to start at all

	    	for(int i = 0; i < gameKeysStrings.length; i++){
	    		// If it's not a duplicate key...
	    		if(gameKeys.add(gameKeysStrings[i])){
	    			// ...check if the game is in the repository.
	    			game = gameRepo.getGame(gameKeysStrings[i]);

			    	if(game == null){
			    		System.out.println("Impossible to start experiment: specified game " + gameKeysStrings[i] + " not found in the repository.");
						return;
			    	}
	    		}
	    	}

			startClock = Integer.parseInt(props.getProperty("startClock"));
			playClock = Integer.parseInt(props.getProperty("playClock"));
			pnCreationTime = Long.parseLong(props.getProperty("pnCreationTime"));
			numParallelPlayers = Integer.parseInt(props.getProperty("numParallelPlayers"));
			matchesPerGamerType = Integer.parseInt(props.getProperty("matchesPerGamerType"));
			numSequentialMatches = Integer.parseInt(props.getProperty("numSequentialMatches"));

			theGamersTypesString = props.getProperty("theGamersTypes").split(";");

			// Check if all internal gamers exist and the settings of the configurable ones are specified
			// Check here so that the tourney won't even start if the gamer types don't exist or are not correctly specified
			gamerTypes = new String[theGamersTypesString.length];
			gamerSettings = new String[theGamersTypesString.length];
			List<String> externalGamerAddresses;
	    	for(int i = 0; i < theGamersTypesString.length; i++){

	    		String[] s = theGamersTypesString[i].split("-");

	    		if(s.length == 1){ // Internal gamer without settings
	    			gamerTypes[i] = s[0];
	    			gamerSettings[i] = null;
	    		}else if(s.length == 2){ // Internal gamer with settings
	    			gamerTypes[i] = s[0];
	    			gamerSettings[i] = s[1];
	    		}else{// External gamer with list of host-port pairs
	    			gamerTypes[i] = s[0];
	    			gamerSettings[i] = null;
	    			if(externalGamersManagers.containsKey(gamerTypes[i])){ // Duplicate external gamer. Gamers must be specified uniquely.
	    				System.out.println("Impossible to start experiment. Duplicate external gamer type " + gamerTypes[i] + ".");
    					return;
	    			}
	    			// Collect all addresses on which the external gamer is available and create the corresponding address manager
	    			externalGamerAddresses = new ArrayList<String>();
	    			for(int j = 1; j < s.length; j=j+2){
	    				externalGamerAddresses.add(s[j] + "-" + s[j+1]);
	    			}
	    			if(externalGamerAddresses.isEmpty()){
	    				System.out.println("Impossible to start experiment. No addresses specified for external gamer type " + gamerTypes[i] + ".");
    					return;
	    			}
	    			externalGamersManagers.put(gamerTypes[i], new ExternalGamerAvailabilityManager(externalGamerAddresses));
	    		}

	    	}

	    	for(int i = 0; i < gamerTypes.length; i++){

	    		// If not an external gamer...
	    		if(!externalGamersManagers.containsKey(gamerTypes[i])){
		    		Class<?> theCorrespondingClass = null;
		    		for (Class<?> gamerClass : ProjectSearcher.INTERNAL_PROPNET_GAMERS.getConcreteClasses()) {
		        		if(gamerClass.getSimpleName().equals(gamerTypes[i])){
		        			theCorrespondingClass = gamerClass;
		        			if(ConfigurableStateMachineGamer.class.isAssignableFrom(theCorrespondingClass)){ // The class is subclass of ConfigurableStateMachineGamer
		        				// If the gamer is configurable than the settings file must be specified
		        				if(gamerSettings[i] == null){
		        					System.out.println("Impossible to start experiment. No settings file specified for gamer type " + gamerTypes[i] + ".");
		        					return;
		        				}
		        			}
		        		}
		        	}
		    		for (Class<?> gamerClass : ProjectSearcher.FPGA_PROPNET_GAMERS.getConcreteClasses()) {
		        		if(gamerClass.getSimpleName().equals(gamerTypes[i])){
		        			theCorrespondingClass = gamerClass;
		        			if(ConfigurableStateMachineGamer.class.isAssignableFrom(theCorrespondingClass)){ // The class is subclass of ConfigurableStateMachineGamer
		        				// If the gamer is configurable than the settings file must be specified
		        				if(gamerSettings[i] == null){
		        					System.out.println("Impossible to start experiment. No settings file specified for gamer type " + gamerTypes[i] + ".");
		        					return;
		        				}
		        			}
		        		}
		        	}
		    		for (Class<?> gamerClass : ProjectSearcher.PROVER_GAMERS.getConcreteClasses()) {
		        		if(gamerClass.getSimpleName().equals(gamerTypes[i])){
		        			theCorrespondingClass = gamerClass;
		        		}
		        	}
		    		if(theCorrespondingClass == null){
		    			System.out.println("Impossible to start experiment. Unexisting gamer type " + gamerTypes[i] + ".");
		    			return;
		    		}
	    		}

			}

	    	// If the tourney is running in parallel on multiple nodes, set continueOldExperiment to false so that the runNumber
	    	// parameter will be considered as referring to one of the parallel runs and won't get modified after the completion
	    	// of this run. Also all checks related to the value of runNumber won't be performed.
	    	if(props.getProperty("continueOldExperiment") != null){
	    		continueOldExperiment = Boolean.parseBoolean(props.getProperty("continueOldExperiment"));
	    	}

	    	if(props.getProperty("finishInterruptedExperiment") != null){
	    		finishInterruptedExperiment = Boolean.parseBoolean(props.getProperty("finishInterruptedExperiment"));
	    	}

			runNumber = Integer.parseInt(props.getProperty("runNumber"));

			if(continueOldExperiment){
				if(!finishInterruptedExperiment && runNumber == 0){
					System.out.println("Trying to start new experiment.");
					timeID = System.currentTimeMillis();
				}else{
					if(finishInterruptedExperiment){
						System.out.println("Trying to finish continuation of old experiment.");
					}else{
						System.out.println("Trying to continue old experiment.");
					}
					String timeIDString = props.getProperty("timeID");
					if(timeIDString == null){
						System.out.println("Impossible to continue experiment. Missing timeID of experiment.");
		    			return;
					}
					timeID = Long.parseLong(timeIDString);
				}
			}else{
				if(finishInterruptedExperiment){
					System.out.println("Trying to finish old experiment.");
					String timeIDString = props.getProperty("timeID");
					if(timeIDString == null){
						System.out.println("Impossible to finish experiment. Missing timeID of experiment.");
		    			return;
					}
					timeID = Long.parseLong(timeIDString);
				}else{
					timeID = System.currentTimeMillis();
				}
			}

			if(props.getProperty("logOnlyEssentialFiles") != null) {
				logOnlyEssentialFiles = Boolean.parseBoolean(props.getProperty("logOnlyEssentialFiles"));
			}

			if(props.getProperty("unlimitedTimeForExternal") != null) {
				unlimitedTimeForExternal = Boolean.parseBoolean(props.getProperty("unlimitedTimeForExternal"));
			}


		} catch (IOException | NumberFormatException e) {
			System.out.println("Impossible to perform experiment, cannot correctly read/write the .properties file for the tourney.");
			e.printStackTrace();
			return;
		}

		String mainLogFolder;

		for(String gameKey : gameKeys){
			mainLogFolder = timeID + "." + tourneyName + "." + gameKey + "." + "Tourney";

			File mainLogFolderFile = new File(mainLogFolder);

			if(!mainLogFolderFile.exists()){
				if(continueOldExperiment){
					if(!finishInterruptedExperiment && runNumber == 0){
						mainLogFolderFile.mkdirs();
					}else{ // If it's not the first run or if we are finishing the first run, the folder must already exist
						if(finishInterruptedExperiment){
							System.out.println("Impossible to finish tourney for game " + gameKey + "! The corresponding folder Doesn't exist! Skipping game.");
						}else{
							System.out.println("Impossible to continue tourney for game " + gameKey + "! The corresponding folder Doesn't exist! Skipping game.");
						}
						continue;
					}
				}else{
					if(finishInterruptedExperiment){
						System.out.println("Impossible to finish tourney for game " + gameKey + "! The corresponding folder Doesn't exist! Skipping game.");
						continue;
					}else{
						mainLogFolderFile.mkdirs();
					}
				}
			}else{
				if(!finishInterruptedExperiment && (!continueOldExperiment || runNumber == 0) ){
					System.out.println("Impossible to start new tourney for game " + gameKey + "! Cannot create folder " + mainLogFolder + " for the tourney. A folder with the same name already exists! Skipping game.");
					continue;
				}
			}

	    	/** 2. Officially start the tourney and start logging. **/

	    	ThreadContext.put("LOG_FOLDER", mainLogFolder);
	    	if(logOnlyEssentialFiles) {
	    		// Add to the files that we want to log only the Stats.csv file and the Errors.log file
	    		GamerLogger.setEssentialLogFileName("Stats");
	    		GamerLogger.setEssentialLogFileName("Errors");
	    	}
	    	GamerLogger.startFileLogging();

	    	String gamerTypesList = "[ ";
	    	for (String s : theGamersTypesString) {
	    		gamerTypesList += (s + " ");
	    	}
	    	gamerTypesList += "]";

	    	GamerLogger.log("TourneyRunner" + runNumber, "Starting tourney " + tourneyName + " for game " + gameKey + " with following settings: START_CLOCK=" +
	    			startClock + "s, PLAY_CLOCK=" + playClock + "s, PROPNET_CREATION_TIME=" + pnCreationTime + "ms, DESIRED_NUM_PARALLEL_PLAYERS=" +
	    			numParallelPlayers + ", MIN_NUM_MATCHES_PER_GAMER_TYPE=" + matchesPerGamerType + ", NUM_SEQUENTIAL_MATCHES=" + numSequentialMatches +
	    			", GAMER_TYPES=" + gamerTypesList + ", CONTINUE_OLD_EXPERIMENT=" + continueOldExperiment + ", FINISH_INTERRUPTED_EXPERIMENT=" +
	    			finishInterruptedExperiment + ", LOG_ONLY_ESSENTIAL_FILES=" + logOnlyEssentialFiles + ", UNLIMITED_TIME_FOR_EXTERNAL=" +
	    			unlimitedTimeForExternal + ".");

	    	/** 3. Compute all combinations of gamer types. **/

	    	game = gameRepo.getGame(gameKey);

	    	int expectedRoles = ExplicitRole.computeRoles(game.getRules()).size();
	    	List<List<Integer>> combinations = Combinator.getCombinations(gamerTypes.length, expectedRoles);

	    	int matchesPerCombination = (matchesPerGamerType / (Combinator.getLastCombinationsPerElement() * Combinator.getLastPermutationsPerCombination()));

	    	if(matchesPerGamerType%(Combinator.getLastCombinationsPerElement() * Combinator.getLastPermutationsPerCombination()) != 0){
	    		matchesPerCombination++;
	    	}

	    	int numMatchRunners = (int) Math.ceil(((double)matchesPerCombination)/((double)numSequentialMatches));

	    	matchesPerCombination = numMatchRunners * numSequentialMatches;

	    	int numParallelMatches = Math.round(((float) numParallelPlayers) / ((float) expectedRoles));

	    	GamerLogger.log("TourneyRunner" + runNumber, "Computed following parameters for tourney: NUM_ROLES=" + expectedRoles +
	    			", NUM_COMBINATIONS=" + combinations.size() + ", NUM_PARALLEL_MATCHES=" + numParallelMatches + ", ACTUAL_NUM_PARALLEL_PLAYERS=" +
	    			numParallelMatches*expectedRoles + ", ACTUAL_NUM_MATCHES_PER_COMBINATION=" + matchesPerCombination + ", ACTUAL_NUM_MATCHES_PER_GAMER_TYPE=" +
	    			(Combinator.getLastCombinationsPerElement() * Combinator.getLastPermutationsPerCombination() * matchesPerCombination) +
	    			", NUM_MATCH_RUNNERS=" + numMatchRunners);

	    	// 5. For each combination run the given amount of match runners.

	    	for(List<Integer> combination : combinations){

	    		// Prompt the JVM to do garbage collection (not sure if really helpful).
	    	    long endGCTime = System.currentTimeMillis() + 3000;
	    	    for (int ii = 0; ii < 1000 && System.currentTimeMillis() < endGCTime; ii++){

	    	    	//System.out.println("Calling GC: " + System.currentTimeMillis());

	    	    	System.gc();
	    	        try {Thread.sleep(1);} catch (InterruptedException lEx) {/* Whatever */}
	    	    }

	    		/*System.out.println("Calling GC.");
	    		for(int i = 0; i < 10; i++){
	    			System.gc();
	    		}*/

	    		String comboIndices = "";
	    		List<String> theComboGamersTypes = new ArrayList<String>();
	    		for(Integer i : combination){
	    			String gamerType = gamerTypes[i.intValue()];
	    			if(gamerSettings[i.intValue()] != null){
	    				gamerType += ("-" + gamerSettings[i.intValue()]);
	    			}
	    			theComboGamersTypes.add(gamerType);
	    			comboIndices += i.toString();
	    		}

	    		GamerLogger.log("TourneyRunner" + runNumber, "Starting sub-tourney for combination " + comboIndices + ".");

	    		ThreadContext.put("LOG_FOLDER", mainLogFolder + "/Combination" + comboIndices);
	    		boolean completed = runMatchesForCombination(runNumber, gameKey, startClock, playClock, pnCreationTime, theComboGamersTypes, numParallelMatches, numMatchRunners, numSequentialMatches, externalGamersManagers, logOnlyEssentialFiles, unlimitedTimeForExternal, comboIndices);
	    		ThreadContext.put("LOG_FOLDER", mainLogFolder);

	    		if(completed){
	    			GamerLogger.log("TourneyRunner" + runNumber, "Ended sub-tourney for combination " + comboIndices + ".");
	    		}else{
	    			GamerLogger.logError("TourneyRunner" + runNumber, "Interrupted sub-tourney for combination " + comboIndices + ".");
	    		}
	    	}

	    	GamerLogger.log("TourneyRunner"+runNumber, "Tourney completed.");
		}

    	// At the end of the experiment, increase the run number
		try {
			reader = new FileReader(propertiesFile);

			Properties props = new Properties();

			// load the properties file:
			props.load(reader);

			reader.close();

			if(!continueOldExperiment || runNumber == 0){
				props.setProperty("timeID", ""+timeID);
			}
			if(continueOldExperiment){
				props.setProperty("runNumber", ""+(runNumber+1));
			}

		    FileWriter writer = new FileWriter(propertiesFile);

		    props.store(writer, null);

		    writer.close();

		} catch (IOException | NumberFormatException e) {
			System.out.println("Impossible to perform experiment, cannot correctly read/write the .properties file for the tourney.");
			return;
		}

	}



	private static boolean runMatchesForCombination(int runNumber, String gameKey, int startClock, int playClock,
			long pnCreationTime, List<String> theGamersTypes, int numParallelMatches, int numMatchRunners,
			int numSequentialMatches, Map<String,ExternalGamerAvailabilityManager> externalGamersManagers,
			boolean logOnlyEssentialFiles, boolean unlimitedTimeForExternal, String comboIndices){

		GamerLogger.log("TourneyRunner"+runNumber, "Starting sub-tourney.");

		// Create the executor as a pool with the desired number of threads
		// (corresponding to the number of matches we want to run in parallel).
		ExecutorService executor = Executors.newFixedThreadPool(numParallelMatches);

		// Create the settings for the process
		List<String> theSettings = new ArrayList<String>();

		// Remeber to change this when the number of settings in the list changes
		int runNumberIndex = 5;

		theSettings.add("java");
		//theSettings.add("/usr/java/jdk1.8.0_131/bin/java"); // To use old java version on go4nature
		//theSettings.add("/usr/java/jdk1.8.0_191-amd64/bin/java"); // To use old java version 8 on goGeneral
		theSettings.add("-Xms4096M");
		theSettings.add("-Xmx4096M");
		//theSettings.add("-XX:+HeapDumpOnOutOfMemoryError");
		theSettings.add("-jar");
//		theSettings.add("C:\\Users\\lucsp\\git\\GGP-Project\\Experiments\\PC\\IndependentSingleMatchRunner.jar");
		theSettings.add("C:\\Users\\lucsp\\git\\GGP-Project\\JARS\\IndependentSingleMatchRunner.jar");
		theSettings.add(ThreadContext.get("LOG_FOLDER"));
		theSettings.add("" + numSequentialMatches);
		theSettings.add("" + 0);
		theSettings.add(gameKey);
		theSettings.add("" + startClock);
		theSettings.add("" + playClock);
		theSettings.add("" + pnCreationTime);
		theSettings.add("" + logOnlyEssentialFiles);
		theSettings.add("" + unlimitedTimeForExternal);

		System.out.println("Working Directory = " + System.getProperty("user.dir"));

		for(int i = (runNumber*numMatchRunners); i < ((runNumber+1)*numMatchRunners); i++){
			theSettings.set(runNumberIndex, ""+i);
			// If the folder of the match already exists, do not run the match
			// Addition to continue experiment that was interrupted while in progress.
			// If the match folder already exists, do not run anything. Run only if the
			// match folder does not exist yet.
			if(new File(ThreadContext.get("LOG_FOLDER") + "/MatchRunner" + i).exists()){
				System.out.println("Match " + i + " for combination " + comboIndices + " already exists, skipping!");
				continue;
			}

			executor.execute(new MatchProcessRunner(i, new ArrayList<String>(theSettings), ThreadContext.get("LOG_FOLDER") + "/MatchRunner" + i, externalGamersManagers, theGamersTypes));
		}

		// Shutdown executor to tell it not to accept any more task to execute.
		// Note that this doesn't interrupt previously started tasks nor avoids executing previously submitted tasks.
		executor.shutdown();

		/*
		while(!(executor.isTerminated())){
			System.out.println("Threads ALL: " + ManagementFactory.getThreadMXBean().getThreadCount());
			System.out.println("Threads ACTIVE: " + Thread.activeCount());
			try {
				executor.awaitTermination(30, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		*/

		// Tell the executor to wait until all currently running tasks have completed execution.
		try {

			executor.awaitTermination(20, TimeUnit.DAYS);

		} catch (InterruptedException e) {
			executor.shutdownNow(); // Interrupt everything
			GamerLogger.logError("TourneyRunner"+runNumber, "Sub-tourney interrupted before completion.");
			GamerLogger.logStackTrace("TourneyRunner"+runNumber, e);
			Thread.currentThread().interrupt();
			return false;
		}

		if(!executor.isTerminated()){
			GamerLogger.logError("TourneyRunner"+runNumber, "Sub-tourney is taking too long. Interrupting it.");
			executor.shutdownNow(); // This instruction interrupts all threads.
			return false;
		}else{
			GamerLogger.log("TourneyRunner"+runNumber, "Sub-tourney completed.");
			return true;
		}
	}


}

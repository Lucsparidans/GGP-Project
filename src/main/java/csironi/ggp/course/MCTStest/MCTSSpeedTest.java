package csironi.ggp.course.MCTStest;

import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.ThreadContext;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.InternalPropnetMCTSManager;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.backpropagation.StandardBackpropagation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.expansion.RandomExpansion;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.movechoice.MaximumScoreChoice;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.playout.RandomPlayout;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.selection.UCTSelection;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.InternalPropnetMCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MCTSMove;
import org.ggp.base.util.game.GameRepository;
import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.match.Match;
import org.ggp.base.util.propnet.architecture.separateExtendedState.immutable.ImmutablePropNet;
import org.ggp.base.util.propnet.creationManager.SeparateInternalPropnetCreationManager;
import org.ggp.base.util.propnet.state.ImmutableSeparatePropnetState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.exceptions.StateMachineInitializationException;
import org.ggp.base.util.statemachine.implementation.internalPropnet.InternalPropnetStateMachine;
import org.ggp.base.util.statemachine.implementation.internalPropnet.SeparateInternalPropnetStateMachine;
import org.ggp.base.util.statemachine.implementation.internalPropnet.structure.InternalPropnetRole;


/**
* This class checks the speed (nodes/second, iterations/second) of the UCT MCTS player
* (decoupled or sequential) that uses the separate propnet state machine.
*
* It is possible to specify the following combinations of main arguments:
*
* 1. [DUCT/SUCT]
* 2. [DUCT/SUCT] [keyOfGameToTest]
* 3. [DUCT/SUCT] [givenInitTime] [maximumTestDuration]
* 4. [DUCT/SUCT] [givenInitTime] [maximumTestDuration] [keyOfGameToTest]
*
* where:
* [DUCT/SUCT] = true if the player must perform Decoupled UCT, false if it must perform Sequential UCT
* 				(DEFAULT: true).
* [givenInitTime] = maximum time in milliseconds that should be spent to initialize the
* 								 propnet state machine (DEFAULT: 420000ms - 7mins).
* [maximumTestDuration] = duration of each test in millisecond (DEFAULT: 60000ms - 1min).
* [keyOfGameToTest] = key of the game to be tested (DEFAULT: null (i.e. all games)).
*
* If nothing or something inconsistent is specified for any of the parameters, the default value will
* be used.
*
* @author C.Sironi
*
*/
public class MCTSSpeedTest {

	/**
	 * Static reference to the logger
	 */
	private static final Logger LOGGER;

	/**
	 * Static reference to the CSV logger
	 */
	private static final Logger CSV_LOGGER;

	static{

		//These properties have to be set here before creating the logger, in the main method is already too late
		System.setProperty("Log4jContextSelector", "org.apache.logging.log4j.core.async.AsyncLoggerContextSelector");
		System.setProperty("isThreadContextMapInheritable", "true");

		LOGGER = LogManager.getRootLogger();
		CSV_LOGGER = LogManager.getLogger("CSVLogger");
	}


	public static void main(String[] args) {

		/*********************** Parse main arguments ****************************/

		boolean DUCT = true;
		long givenInitTime = 420000L;
		long testTime = 60000L;
		String gameToTest = null;

		if(args.length != 0){ // At least one argument is specified and the first argument is either true or false

			DUCT = Boolean.parseBoolean(args[0]);

			if (args.length <= 4){

				if(args.length == 4 || args.length == 2){
					gameToTest = args[args.length-1];
				}
				if(args.length == 4 || args.length == 3){
					try{
						givenInitTime = Long.parseLong(args[1]);
					}catch(NumberFormatException nfe){
						System.out.println("Inconsistent maximum initialization time specification! Using default value.");
						givenInitTime = 420000L;
					}
					try{
						testTime = Long.parseLong(args[2]);
					}catch(NumberFormatException nfe){
						System.out.println("Inconsistent test duration specification! Using default value.");
						testTime = 60000L;
					}
				}
			}else{ // Too many arguments, using default
				System.out.println("Inconsistent number of main arguments! Ignoring them.");
			}

		} // else use default values

		String s;
		if(DUCT){
			s = "DUCT";
		}else{
			s = "SUCT";
		}

		System.out.println("Testing speed of " + s + "/MCTS using the propnet state machine.");

		if(gameToTest == null){
			System.out.println("Running tests on ALL games with the following time settings:");
		}else{
			System.out.println("Running tests on game " + gameToTest + " with the following time settings:");
		}
		System.out.println("Propnet building time: " + givenInitTime + "ms");
		System.out.println("Running time for each test: " + testTime + "ms");
		System.out.println();


		/*********************** Perform all the tests ****************************/

		InternalPropnetStateMachine thePropnetMachine;

		ThreadContext.put("LOG_FOLDER", System.currentTimeMillis() + "-" + s + "MCTSSpeedTest");

		ThreadContext.put("LOG_FILE", s + "MCTSSpeedTestTable");

	    CSV_LOGGER.info("Game key;#Roles;PN Construction Time (ms);PN Initialization Time (ms);SM initialization time;Test Duration (ms);Search time(ms);Iterations;Visited Nodes;Iterations/second;Nodes/second;Playing role;Chosen move;Scores sum;Visits;AverageScore");

	    GameRepository theRepository = GameRepository.getDefaultRepository();
	    for(String gameKey : theRepository.getGameKeys()) {
	        if(gameKey.contains("laikLee")) continue;

	        //if(gameKey.equals("ticTacHeaven")) continue;

	        // TODO: change code so that if there is only one game to test we won't run through the whole sequence of keys.
	        if(gameToTest != null && !gameKey.equals(gameToTest)) continue;

	        System.out.println("Detected activation in game " + gameKey + ".");

	        Match fakeMatch = new Match(gameKey + "." + System.currentTimeMillis(), -1, -1, -1,theRepository.getGame(gameKey));

	        ThreadContext.put("LOG_FILE", fakeMatch.getMatchId() + "-" + s + "MCTSSpeedTester");

	        LOGGER.info("[" + s + "MCTSSpeedTest] Testing on game " + gameKey);

	        List<Gdl> description = theRepository.getGame(gameKey).getRules();

	        // Create the executor service that will run the propnet manager that creates the propnet
	        ExecutorService executor = Executors.newSingleThreadExecutor();

	        // Create the propnet creation manager
	        SeparateInternalPropnetCreationManager manager = new SeparateInternalPropnetCreationManager(description, System.currentTimeMillis() + givenInitTime);

	  	  	// Start the manager
	  	  	executor.execute(manager);

	  	  	// Shutdown executor to tell it not to accept any more task to execute.
			// Note that this doesn't interrupt previously started tasks.
			executor.shutdown();

			// Tell the executor to wait until the currently running task has completed execution or the timeout has elapsed.
			try{
				executor.awaitTermination(givenInitTime, TimeUnit.MILLISECONDS);
			}catch(InterruptedException e){ // The thread running the verifier has been interrupted => stop the test
				executor.shutdownNow(); // Interrupt everything
				LOGGER.error("[" + s + "MCTSSpeedTest] " + s + "/MCTS speed test interrupted. Test on game "+ gameKey +" won't be completed.", e);
				Thread.currentThread().interrupt();
				return;
			}

			// Here the available time has elapsed, so we must interrupt the thread if it is still running.
			executor.shutdownNow();

			// Wait for the thread to actually terminate
			while(!executor.isTerminated()){

				// If the thread didn't terminate, wait for a minute and then check again
				try{
					executor.awaitTermination(1, TimeUnit.MINUTES);
				}catch(InterruptedException e) {
					// If this exception is thrown it means the thread that is executing the speed test
					// of the DUCT/MCTS has been interrupted. If we do nothing this state machine could be stuck in the
					// while loop anyway until all tasks in the executor have terminated, thus we break out of the loop and return.
					// What happens to the still running tasks in the executor? Who will make sure they terminate?
					LOGGER.error("[" + s + "MCTSSpeedTest] State mahcine verification interrupted. Test on game "+ gameKey +" won't be completed.", e);
					Thread.currentThread().interrupt();
					return;
				}
			}

			// If we are here it means that the manager stopped running. We must check if it has created a usable propnet or not.

			ImmutablePropNet propnet = manager.getImmutablePropnet();
			ImmutableSeparatePropnetState propnetState = manager.getInitialPropnetState();

			// Create the state machine giving it the propnet and the propnet state.
			// NOTE that if any of the two is null, it means that the propnet creation/initialization went wrong
			// and this will be detected by the state machine during initialization.
		    thePropnetMachine = new SeparateInternalPropnetStateMachine(propnet, propnetState);

		    int numRoles = -1;
	        long initializationTime;
	        long testDuration = -1L;
	        long searchTime = -1L;
	        int iterations = -1;
	        int visitedNodes = -1;
	        double iterationsPerSecond = -1;
	        double nodesPerSecond = -1;
	        Role playingRole = null;
	        Move chosenMove = null;
	        long scoresSum = -1L;
	        long visits = -1;
	        double averageScore = -1;
	        InternalPropnetMCTSManager MCTSmanager = null;

	        // Try to initialize the propnet state machine.
	        // If initialization fails, skip the test.
	    	long initStart = System.currentTimeMillis();
	        try{
	        	thePropnetMachine.initialize(description, initStart + givenInitTime);

	        	initializationTime = System.currentTimeMillis() - initStart;

	        	System.out.println("Propnet creation succeeded. Checking speed.");



		        /***************************************/
		        System.gc();
		        /***************************************/

		        Random r = new Random();
		        double c = 0.7;
		        double uctOffset = 0.01;
		        int gameStep = 1;
		        int gameStepOffset = 2;
		        int maxSearchDepth = 500;

		        long testStart = System.currentTimeMillis();

		        LOGGER.info("[" + s + "MCTSSpeedTest] Starting speed test.");

		        InternalPropnetRole internalPlayingRole = thePropnetMachine.getInternalRoles()[0];
		        playingRole = thePropnetMachine.internalRoleToRole(internalPlayingRole);
		        numRoles = thePropnetMachine.getInternalRoles().length;

		        MCTSmanager = new InternalPropnetMCTSManager(DUCT, internalPlayingRole,
		        		new UCTSelection(numRoles, internalPlayingRole, r, uctOffset, c),
		        		new RandomExpansion(numRoles, internalPlayingRole, r), new RandomPlayout(thePropnetMachine),
		        		new StandardBackpropagation(numRoles, internalPlayingRole),
		        		new MaximumScoreChoice(internalPlayingRole, r), thePropnetMachine, gameStepOffset, maxSearchDepth);

		        try{
		        	LOGGER.info("[" + s + "MCTSSpeedTest] Starting search.");

		        	InternalPropnetMCTSNode initialNode = MCTSmanager.search(thePropnetMachine.getInternalInitialState(), System.currentTimeMillis() + testTime, gameStep);
		        	MCTSMove finalMove = MCTSmanager.getBestMove(initialNode);

		        	LOGGER.info("[" + s + "MCTSSpeedTest] Search ended correctly.");
		        	chosenMove = thePropnetMachine.internalMoveToMove(finalMove.getTheMove());
		 	        scoresSum = finalMove.getScoreSum();
		 	        visits = finalMove.getVisits();
		 	        if(visits != 0){
		 	        	averageScore = ((double) scoresSum) / ((double) visits);
		 	        }
		 	        iterations = MCTSmanager.getIterations();
		        	visitedNodes = MCTSmanager.getVisitedNodes();
		        	searchTime = MCTSmanager.getSearchTime();
		        	if(searchTime != 0){
			        	iterationsPerSecond = ((double) iterations * 1000)/((double) searchTime);
			        	nodesPerSecond = ((double) visitedNodes * 1000)/((double) searchTime);
		        	}
		        }catch(Exception e){
		        	LOGGER.error("[" + s + "MCTSSpeedTest] MCTS failed during execution. Impossible to continue the speed test. Cause: [" + e.getClass().getSimpleName() + "] " + e.getMessage(), e);
		        	System.out.println("Stopping test on game " + gameKey + ". Exception during search execution.");
		        }

		        testDuration = System.currentTimeMillis() - testStart;
	        }catch(StateMachineInitializationException e){
	        	initializationTime = System.currentTimeMillis() - initStart;
	        	LOGGER.error("[" + s + "MCTSSpeedTest] State machine " + thePropnetMachine.getName() + " initialization failed, impossible to test this game. Cause: [" + e.getClass().getSimpleName() + "] " + e.getMessage(), e);
	        	System.out.println("Skipping test on game " + gameKey + ". No propnet available.");
	        }

	        LOGGER.info("");

	        ThreadContext.put("LOG_FILE", s + "MCTSSpeedTestTable");

	        CSV_LOGGER.info(gameKey + ";" + numRoles + ";" + manager.getPropnetConstructionTime() + ";" + manager.getTotalInitTime() + ";" + initializationTime + ";" + testDuration + ";" + searchTime + ";" + iterations + ";" + visitedNodes + ";" + iterationsPerSecond + ";" + nodesPerSecond + ";" + playingRole + ";" + chosenMove + ";" + scoresSum + ";" + visits + ";" + averageScore + ";");


	        // ERROR TEST - START
	        /*
	        ThreadContext.put("LOG_FILE", "The prover error test");

	        if(MCTSmanager != null){

	        	System.out.println();

	        	List<List<Move>> errorJointMoves = MCTSmanager.getToGet();

	        	StateMachine prover = new ProverStateMachine();
	        	try {
					prover.initialize(description, Long.MAX_VALUE);
				} catch (StateMachineInitializationException e) {
					System.out.println("EXCEPTION INIT: Impossible to try error path.");
					e.printStackTrace();
					return;
				}

	        	MachineState state = prover.getInitialState();

	        	int i = 0;

	        	for(List<Move> m : errorJointMoves){

	        		System.out.println();
	        		System.out.println("State " + i + ":");

	        		List<List<Move>> legalMoves = new ArrayList<List<Move>>();

	        		int roleIndex = 0;

	        		for(Role r : prover.getRoles()){
	        			try {
	        				List<Move> lm = prover.getLegalMoves(state, r);
							legalMoves.add(lm);
							System.out.println("Legal moves for " + r + ":");
							System.out.println(lm);
							if(!(lm.contains(m.get(roleIndex)))){
								System.out.println("MOVE " + m.get(roleIndex) + " NOT FOUND: Impossible to try error path.");
								return;
							}
						} catch (MoveDefinitionException | StateMachineException e) {
							System.out.println("EXCEPTION LEGAL MOVES: Impossible to try error path.");
							e.printStackTrace();
							return;
						}
	        			roleIndex++;
	        		}

	        		try {
						state = prover.getNextState(state, m);
					} catch (TransitionDefinitionException | StateMachineException e) {
						System.out.println("EXCEPTION NEXT STATE: Impossible to try error path.");
						System.out.println("Cannot go further than state " + state);
						e.printStackTrace();
						return;
					}

	        		i++;
	        	}

	        	System.out.println();
	        	System.out.println("Reached error state: " + state);

	        	List<Move> randomJointMove;
	        	try {
					randomJointMove = prover.getRandomJointMove(state);
				} catch (MoveDefinitionException | StateMachineException e) {
					System.out.println("EXCEPTION JOINT MOVE: Impossible to get joint move!");
					e.printStackTrace();
					return;
				}

	        	System.out.println("TRAGEDY! There must be a bug in your code cause the prover works!");
	        	System.out.println("It found joint move: " + randomJointMove + "!");

	        }else{
	        	System.out.println("NULL MANAGER: Impossible to try error path.");
	        	return;
	        }
	        */
	        // ERROR TEST - END

	        /***************************************/
	        System.gc();
	        GdlPool.drainPool();
	        /***************************************/

	    }
	}

}

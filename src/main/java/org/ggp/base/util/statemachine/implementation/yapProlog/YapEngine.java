/**
 *
 */
package org.ggp.base.util.statemachine.implementation.yapProlog;

import java.io.File;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.Move;
import org.ggp.base.util.statemachine.Role;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.implementation.yapProlog.transform.YapEngineSupport;

import com.declarativa.interprolog.YAPSubprocessEngine;

/**
 * @author Dubs
 *
 */
public class YapEngine{

	// File where to put the game description
	private static final String descriptionFile = "C:\\Users\\c.sironi\\\"BITBUCKET REPOS\"\\GGP-Base\\csironi\\yapPrologFiles\\description.pl";
	private static final File fileDescription = new File(descriptionFile);

	// File with all the predefined Prolog functions
	private static final String functionsFile = "C:\\Users\\c.sironi\\\"BITBUCKET REPOS\"\\GGP-Base\\csironi\\yapPrologFiles\\prologFunctions.pl";
	private static final File fileFunctions = new File(functionsFile);
	// Second file with all the predefined Prolog functions
	// => using the Internal DataBase
	private static final String functionsFileIdb = "C:\\Users\\c.sironi\\\"BITBUCKET REPOS\"\\GGP-Base\\csironi\\yapPrologFiles\\prologFunctionsIdb.pl";
	private static final File fileFunctionsIdb = new File(functionsFileIdb);

	// Where is the YAP directory
	private static final String yapDirectory = "C:\\\"Program Files\"\\Yap64\\bin\\yap";

	// The YapEngineSupport which handles the translations and the mapping
	private YapEngineSupport support;

	// The YAPSubprocessEngine used
	private YAPSubprocessEngine engine;

	// The state in the Prolog side
	// -> to avoid running "computeState(MachineState)" when it's useless
	private Set<GdlSentence> currentState;

	// The list of the roles
	// useful for "getRandomJointMove(MachineState)"
	private static List<Role> roles;
	private static List<String> fakeRoles;

	// If 'true', use the queries objects and the executor
	// Otherwise, use the basic execution of "deterministicGoal(_)"
	private static boolean THREAD;
	// If 'true', use the Prolog internal database
	// Otherwise, use the "assert-retract" way
	private static boolean IDB;

	// The ExecutorService which kill a query object if timeout
	ExecutorService executor;

	// The TimeUnit used to run "executor.invokeAny(_)"
	private static final TimeUnit TU = TimeUnit.MILLISECONDS;

	//The backing StateMachine to handle the InterProlog-Prolog crashes
	private StateMachine backingStateMachine;



	/**
	 * Launch the YAPSubprocessEngine
	 * 		+ create the YapEngineSupport
	 * 		+ write the game description on the description file
	 * 		+ ask YAP Prolog to consult the functions file
	 * 		+ initialize the queries objects
	 * 		+ randomize Prolog
	 * 		+ create the ExecutorService
	 * 		+ initialize the backing StateMachine
	 */
	public YapEngine(List<Gdl> description, boolean thread, boolean idb, StateMachine backingStateMachine)
	{
		this.backingStateMachine = backingStateMachine;
		THREAD = thread;
		IDB = idb;

		support = new YapEngineSupport();
		engine = new YAPSubprocessEngine(yapDirectory);
		//YAPSubprocessEngineWindow yapseW = new YAPSubprocessEngineWindow(engine);

		flushAndWrite(support.toProlog(description));

		if(IDB) engine.consultAbsolute(fileFunctionsIdb);
		else engine.consultAbsolute(fileFunctions);

		initializeQueries();
		randomizeProlog();

		executor = Executors.newSingleThreadExecutor();

		initializeBackingStateMachine(description);
	}



	/**
	 * Initialize the backingStateMachine
	 * usually : ProverStateMahine
	 */
	private void initializeBackingStateMachine(List<Gdl> description)
	{
		class BackingSMInitialize implements Callable<Boolean>
		{
			private List<Gdl> rulessheet;
			public BackingSMInitialize(List<Gdl> description)
			{
				rulessheet = description;
			}
			@Override
			public Boolean call()
			{
				backingStateMachine.initialize(rulessheet);
				return true;
			}
		}

		BackingSMInitialize bsmInit = new BackingSMInitialize(description);

		try{
			executor.invokeAny(Arrays.asList(bsmInit), 5000, TU);
		}
		catch(TimeoutException te){
			System.err.println("backingStateMachine INITIALIZE Timeout");
			te.printStackTrace();
		}
		catch(NullPointerException ne){
			System.err.println("backingStateMachine INITIALIZE NullPointer");
			ne.printStackTrace();
		}
		catch(Exception e){
			System.err.println("backingStateMachine INITIALIZE __");
			e.printStackTrace();
		}

	}



	/**
	 * Stop the YapSubprocessEngine
	 * Used when the match ends normally or abruptly
	 */
	public void stop()
	{
		executor.shutdown();
		engine.shutdown();
	}



	/**
	 * Change the Prolog random number generator
	 * using the Java random number generator
	 */
	private void randomizeProlog()
	{
		int i = (int)Math.min(Math.random()*(30268), 30268)+1;
		int j = (int)Math.min(Math.random()*(30307), 30307)+1;
		int k = (int)Math.min(Math.random()*(30323), 30323)+1;
		engine.realCommand("setrand(rand("+i+", "+j+", "+k+"))");
	}



	/**
	 * Re-initialize the YAPSubprocessEngine after it crashes
	 */
	private void reInitialize()
	{
		executor.shutdown();
		engine.shutdown();

		engine = new YAPSubprocessEngine(yapDirectory);

		if(IDB) engine.consultAbsolute(fileFunctionsIdb);
		else engine.consultAbsolute(fileFunctions);

		randomizeProlog();
		executor = Executors.newSingleThreadExecutor();
	}



	/**
	 * Compute the initial state of the game
	 */
	public MachineState computeInitialStateGdl()
	{
		System.out.println("COMPUTE_INITIAL_STATE_GDL");

		if(!THREAD)
		{
			try{
				currentState = support.askToState((String[]) engine.deterministicGoal("initialize_state(List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("computeInitalStateGdl");
			}
			return new MachineState(currentState);
		}
		else
		{
			try{
				currentState = support.askToState(executor.invokeAny(Arrays.asList(QUERYaosComputeInitialStateGdl), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : computeInitalStateGdl");
				reInitialize();
				return backingStateMachine.getInitialState();
			}
			catch(NullPointerException ne){
				System.err.println("NULL : computeInitalStateGdl");
				reInitialize();
				return backingStateMachine.getInitialState();
			}
			catch(Exception e){
				System.err.println("THREAD : computeInitalStateGdl");
				reInitialize();
				return backingStateMachine.getInitialState();
			}
			return new MachineState(currentState);
		}
	}



	/**
	 * Compute the roles of the game
	 */
	public List<Role> computeRoles()
	{
		if(!THREAD)
		{
			roles = support.askToRoles((String[]) engine.deterministicGoal("get_roles(List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
			fakeRoles = support.getFakeRoles(roles);
			return roles;
		}
		else
		{
			try{
				roles = support.askToRoles(executor.invokeAny(Arrays.asList(QUERYaosComputeRoles), WaitForQuery, TU));
				fakeRoles = support.getFakeRoles(roles);
				return roles;
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : computeRoles");
				reInitialize();
				return backingStateMachine.getRoles();
			}
			catch(NullPointerException ne){
				System.err.println("NULL : computeRoles");
				reInitialize();
				return backingStateMachine.getRoles();
			}
			catch(Exception e){
				System.err.println("THREAD : computeRoles");
				reInitialize();
				return backingStateMachine.getRoles();
			}
		}
	}



	/**
	 * Returns TRUE if the state is terminal, FALSE otherwise
	 */
	public boolean isTerminal(MachineState machine)
	{
		//computeState(machine);

		//System.out.println("isTerminal");

		if(!THREAD)
		{
			try{
				computeState(machine);
				if(engine.deterministicGoal("is_terminal") == true) return true;
				else return false;
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("isTerminal");
			}
			return false;
		}
		else
		{
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return executor.invokeAny(Arrays.asList(QUERYbIsTerminal), WaitForQuery, TU);
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : isTerminal");
				reInitialize();
				System.out.println("backingStateMachine : isTerminal");
				boolean temp = backingStateMachine.isTerminal(machine);
				System.out.println("TERMINAL :"+temp);
				return temp;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : isTerminal");
				reInitialize();
				System.out.println("backingStateMachine : isTerminal");
				boolean temp = backingStateMachine.isTerminal(machine);
				System.out.println("TERMINAL :"+temp);
				return temp;
			}
			catch(Exception e){
				System.err.println("THREAD : isTerminal");
				reInitialize();
				System.out.println("backingStateMachine : isTerminal");
				boolean temp = backingStateMachine.isTerminal(machine);
				System.out.println("TERMINAL :"+temp);
				return temp;
			}
		}
	}



	/**
	 * Returns the goal value for the given role in the given state
	 */
	public int getGoal(MachineState machine, Role role)
	{
		//computeState(machine);

		//System.out.println("getGoal");

		if(!THREAD)
		{
			try{
				computeState(machine);
				return Integer.parseInt((String) engine.deterministicGoal("get_goal("+support.getFakeRole(role)+", S)", "[string(S)]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getGoal");
			}
			return 0;
		}
		else
		{
			QUERYsGetGoal.setSubGoal(support.getFakeRole(role));
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return Integer.parseInt(executor.invokeAny(Arrays.asList(QUERYsGetGoal), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getGoal");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getGoal");
					int temp = backingStateMachine.getGoal(machine, role);
					System.out.println("GOAL :"+temp);
					return temp;
				}
				catch(GoalDefinitionException gde){
					gde.printStackTrace();
				}
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getGoal");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getGoal");
					int temp = backingStateMachine.getGoal(machine, role);
					System.out.println("GOAL :"+temp);
					return temp;
				}
				catch(GoalDefinitionException gde){
					gde.printStackTrace();
				}
			}
			catch(Exception e){
				System.err.println("THREAD : getGoal");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getGoal");
					int temp = backingStateMachine.getGoal(machine, role);
					System.out.println("GOAL :"+temp);
					return temp;
				}
				catch(GoalDefinitionException gde){
					gde.printStackTrace();
				}
			}
			return 0;
		}
	}



	/**
	 * Returns a list containing every move that is legal for the given role in the
	 * given state
	 */
	public List<Move> getLegalMoves(MachineState machine, Role role)
	{
		//computeState(machine);

		//System.out.println("getLegalMoves");

		if(!THREAD)
		{
			try{
				computeState(machine);
				return support.askToMoves((String[]) engine.deterministicGoal("get_legal_moves("+support.getFakeRole(role)+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getLegalMoves");
			}
			return new LinkedList<Move>();
		}
		else
		{
			QUERYaosGetLegalMoves.setSubGoal(""+support.getFakeRole(role));
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return support.askToMoves(executor.invokeAny(Arrays.asList(QUERYaosGetLegalMoves), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getLegalMoves");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getLegalMoves");
					List<Move> temp = backingStateMachine.getLegalMoves(machine, role);
					System.out.println("LEGALMOVES :"+temp);
					return temp;
				}
				catch(MoveDefinitionException mde){
					mde.printStackTrace();
				}
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getLegalMoves");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getLegalMoves");
					List<Move> temp = backingStateMachine.getLegalMoves(machine, role);
					System.out.println("LEGALMOVES :"+temp);
					return temp;
				}
				catch(MoveDefinitionException mde){
					mde.printStackTrace();
				}
			}
			catch(Exception e){
				System.err.println("THREAD : getLegalMoves");
				reInitialize();
				try{
					System.out.println("backingStateMachine : getLegalMoves");
					List<Move> temp = backingStateMachine.getLegalMoves(machine, role);
					System.out.println("LEGALMOVES :"+temp);
					return temp;
				}
				catch(MoveDefinitionException mde){
					mde.printStackTrace();
				}
			}
			return new LinkedList<Move>();
		}
	}



	/**
	 * Compute the next state for a given list of Move and a given list of Role
	 * 	(in the same order)
	 */
	public MachineState getNextState(MachineState machine, List<Move> moves)
	{
		//computeState(machine);

		//System.out.println("getNextState");

		if(moves.size()==roles.size())
		{
			if(!THREAD)
			{
				try{
					computeState(machine);
					currentState = support.askToState((String[]) engine.deterministicGoal("get_next_state("+fakeRoles+", "+support.getFakeMoves(moves)+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
					return new MachineState(currentState);
				}
				catch(Exception e){
					e.printStackTrace();
					System.err.println("getNextState");
				}
			}
			else
			{
				QUERYaosGetNextState.setSubGoal(fakeRoles+", "+support.getFakeMoves(moves));
				try{
					if(!currentState.equals(machine.getContents()))
					{
						QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
						if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
						else currentState = support.askToState(executor.invokeAny(Arrays.asList(QUERYaosGetNextState), WaitForQuery, TU));
					}
					else currentState = support.askToState(executor.invokeAny(Arrays.asList(QUERYaosGetNextState), WaitForQuery, TU));
				}
				catch(TimeoutException te){
					System.err.println("TIMEOUT : getNextState");
					reInitialize();
					try{
						System.out.println("backingStateMachine : getNextState");
						MachineState temp = backingStateMachine.getNextState(machine, moves);
						System.out.println("NEW MachineState :"+temp);
						return temp;
					}
					catch(TransitionDefinitionException tde){
						tde.printStackTrace();
					}
				}
				catch(NullPointerException ne){
					System.err.println("NULL : getNextState");
					reInitialize();
					try{
						System.out.println("backingStateMachine : getNextState");
						MachineState temp = backingStateMachine.getNextState(machine, moves);
						System.out.println("NEW MachineState :"+temp);
						return temp;
					}
					catch(TransitionDefinitionException tde){
						tde.printStackTrace();
					}
				}
				catch(Exception e){
					System.err.println("THREAD : getNextState");
					reInitialize();
					try{
						System.out.println("backingStateMachine : getNextState");
						MachineState temp = backingStateMachine.getNextState(machine, moves);
						System.out.println("NEW MachineState :"+temp);
						return temp;
					}
					catch(TransitionDefinitionException tde){
						tde.printStackTrace();
					}
				}
				return new MachineState(currentState);
			}
		}
		return machine;
	}



	/**
	 * Compute the given MachineState in the Prolog side
	 */
	private void computeState(MachineState machine)
	{
		if(!currentState.equals(machine.getContents())){

			//System.out.println("computeState");

			if(!THREAD)
			{
				try{
					if(!((String) engine.deterministicGoal("compute_state("+support.getFakeMachineState(machine.getContents())+", S)", "[string(S)]") [0]).equals("d")) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				catch(Exception e){
					e.printStackTrace();
					System.err.println("computeState");
				}
			}
			else
			{
				QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
				try{
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				catch(TimeoutException te){
					System.err.println("TIMEOUT : computeState");
					reInitialize();
				}
				catch(NullPointerException ne){
					System.err.println("NULL : computeState");
					reInitialize();
				}
				catch(Exception e){
					System.err.println("THREAD : computeState");
					reInitialize();
				}
			}
		}
	}





	/**
	 * Returns a random Move from the list containing all the legal moves for
	 * the given role in the given state
	 */
	public Move getRandomMove(MachineState machine, Role role)
	{
		//computeState(machine);

		//System.out.println("getRandomMove");

		if(!THREAD)
		{
			try{
				computeState(machine);
				return support.askToMove((String) engine.deterministicGoal("get_random_move("+support.getFakeRole(role)+", S)", "[string(S)]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getRandomMove");
			}
			return null;
		}
		else
		{
			QUERYsGetRandomMove.setSubGoal(support.getFakeRole(role));
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return support.askToMove(executor.invokeAny(Arrays.asList(QUERYsGetRandomMove), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getRandomMove");
				reInitialize();
				return null;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getRandomMove");
				reInitialize();
				return null;
			}
			catch(Exception e){
				System.err.println("THREAD : getRandomMove");
				reInitialize();
				return null;
			}
		}
	}



	/**
	 * Returns a random joint move from among all the possible joint moves in
	 * the given state
	 */
	public List<Move> getRandomJointMove(MachineState machine)
	{
		//computeState(machine);

		//System.out.println("getRandomJointMove(_)");

		if(!THREAD)
		{
			try{
				computeState(machine);
				return support.askToMoves((String[]) engine.deterministicGoal("get_random_joint_move("+fakeRoles+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getRandomJointMove(_)");
			}
			return null;
		}
		else
		{
			QUERYaosGetRandomJointMove1.setSubGoal(""+fakeRoles);
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return support.askToMoves(executor.invokeAny(Arrays.asList(QUERYaosGetRandomJointMove1), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getRandomJointMove(_)");
				reInitialize();
				return null;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getRandomJointMove(_)");
				reInitialize();
				return null;
			}
			catch(Exception e){
				System.err.println("THREAD : getRandomJointMove(_)");
				reInitialize();
				return null;
			}
		}
	}



	/**
	 * Returns a random joint move from among all the possible joint moves in
	 * the given state in which the given role makes the given move
	 */
	public List<Move> getRandomJointMove(MachineState machine, Role role, Move move)
	{
		//computeState(machine);

		//System.out.println("getRandomJointMove(_,_,_)");

		if(!THREAD)
		{
			try{
				computeState(machine);
				return support.askToMoves((String[]) engine.deterministicGoal("get_random_joint_moveg("+fakeRoles+", "+support.getFakeRole(role)+", "+support.getFakeMove(move)+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getRandomJointMove(_,_,_)");
			}
			return null;
		}
		else
		{
			QUERYaosGetRandomJointMove2.setSubGoal(fakeRoles+", "+support.getFakeRole(role)+", "+support.getFakeMove(move));
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = machine.getContents();
				}
				return support.askToMoves(executor.invokeAny(Arrays.asList(QUERYaosGetRandomJointMove2), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getRandomJointMove(_,_,_)");
				reInitialize();
				return null;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getRandomJointMove(_,_,_)");
				reInitialize();
				return null;
			}
			catch(Exception e){
				System.err.println("THREAD : getRandomJointMove(_,_,_)");
				reInitialize();
				return null;
			}
		}
	}



	/**
	 * Returns a random next state of the game from the possible next states
	 * resulting from the given role playing the given move
	 */
	public MachineState getRandomNextState(MachineState machine, Role role, Move move)
	{
		//computeState(machine);

		//System.out.println("getRandomNextState");

		if(!THREAD)
		{
			try{
				computeState(machine);
				currentState = support.askToState((String[]) engine.deterministicGoal("get_random_next_state("+fakeRoles+", "+support.getFakeRole(role)+", "+support.getFakeMove(move)+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
				return new MachineState(currentState);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("getRandomNextState");
			}
		}
		else
		{
			QUERYaosGetRandomNextState.setSubGoal(fakeRoles+", "+support.getFakeRole(role)+", "+support.getFakeMove(move));
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = support.askToState(executor.invokeAny(Arrays.asList(QUERYaosGetRandomNextState), WaitForQuery, TU));
				}
				else currentState = support.askToState(executor.invokeAny(Arrays.asList(QUERYaosGetRandomNextState), WaitForQuery, TU));
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : getRandomNextState");
				reInitialize();
				return null;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : getRandomNextState");
				reInitialize();
				return null;
			}
			catch(Exception e){
				System.err.println("THREAD : getRandomNextState");
				reInitialize();
				return null;
			}
			return new MachineState(currentState);
		}
		//return machine.getContents();
		return null;
	}



	/**
	 * Returns a terminal state derived from repeatedly making random joint moves
	 * until reaching the end of the game
	 */
	public MachineState performDepthCharge(MachineState machine, final int[] theDepth)
	{
		//computeState(machine);

		//System.out.println("performDepthCharge");

		if(!THREAD)
		{
			try{
				computeState(machine);
				currentState = support.askToState((String[]) engine.deterministicGoal("perform_depth_charge("+fakeRoles+", List), processList(List, LL), ipObjectTemplate('ArrayOfString',AS,_,[LL],_)", "[AS]") [0]);
				return new MachineState(currentState);
			}
			catch(Exception e){
				e.printStackTrace();
				System.err.println("performDepthCharge");
			}
		}
		else
		{
			QUERYaosPerformDepthCharge.setSubGoal(""+fakeRoles);
			try{
				if(!currentState.equals(machine.getContents()))
				{
					QUERYsComputeState.setSubGoal(""+support.getFakeMachineState(machine.getContents()));
					if( !(executor.invokeAny(Arrays.asList(QUERYsComputeState), WaitForQuery, TU)).equals("d") ) System.err.println("ERROR : computeState");
					else currentState = support.askToStatePerform(executor.invokeAny(Arrays.asList(QUERYaosPerformDepthCharge), WaitForQuery, TU));
				}
				else currentState = support.askToStatePerform(executor.invokeAny(Arrays.asList(QUERYaosPerformDepthCharge), WaitForQuery, TU));
				theDepth[0] = support.getPerformDepth();
			}
			catch(TimeoutException te){
				System.err.println("TIMEOUT : performDepthCharge");
				reInitialize();
				return null;
			}
			catch(NullPointerException ne){
				System.err.println("NULL : performDepthCharge");
				reInitialize();
				return null;
			}
			catch(Exception e){
				System.err.println("THREAD : performDepthCharge");
				reInitialize();
				return null;
			}
			return new MachineState(currentState);
		}
		//return machine.getContents();
		return null;
	}



	/**
	 *	Flush the description file and write the game description in it
	 * @param string: the description of the game
	 */
	private boolean flushAndWrite(StringBuffer string)
	{
		FileOutputStream out;
		PrintStream p;
		try{
			out = new FileOutputStream(fileDescription);
			out.flush();
			p = new PrintStream( out );
			p.print(string);
			p.close();
			return true;
		}
		catch(Exception e){
			System.err.println("ERROR : flushAndWrite("+fileDescription+")");
			return false;
		}
	}



	/**
	 * Initialize the queries objects
	 */
	private void initializeQueries()
	{
		QUERYbIsTerminal = new QueryB();

		QUERYsComputeState = new QueryS("compute_state(");
		QUERYsGetGoal = new QueryS("get_goal(");

		QUERYaosGetNextState = new QueryAOS("get_next_state(");
		QUERYaosGetLegalMoves = new QueryAOS("get_legal_moves(");
		QUERYaosComputeRoles = new QueryAOS(true, "get_roles(_l), processList(_l, _ll), ipObjectTemplate('ArrayOfString',_lll,_,[_ll],_)");
		QUERYaosComputeInitialStateGdl = new QueryAOS(true, "initialize_state(_l), processList(_l, _ll), ipObjectTemplate('ArrayOfString',_lll,_,[_ll],_)");

		QUERYsGetRandomMove = new QueryS("get_random_move(");
		QUERYaosGetRandomJointMove1 = new QueryAOS("get_random_joint_move(");
		QUERYaosGetRandomJointMove2 = new QueryAOS("get_random_joint_moveg(");
		QUERYaosGetRandomNextState = new QueryAOS("get_random_next_state(");

		QUERYaosPerformDepthCharge = new QueryAOS("perform_depth_charge(");
	}



	/**
	 *  The queries objects used to call "deterministicGoal(_)"
	 */
	// boolean query
	private static QueryB QUERYbIsTerminal; // to "isTerminal" method

	// String queries
	private static QueryS QUERYsComputeState; // to "computeState" method
	private static QueryS QUERYsGetGoal; // to "getGoal" method

	// ArrayOfString queries
	private static QueryAOS QUERYaosGetNextState; // to "getNextState" method
	private static QueryAOS QUERYaosGetLegalMoves; // to "getLegalMoves" method
	private static QueryAOS QUERYaosComputeRoles; // to "computeRoles" method
	private static QueryAOS QUERYaosComputeInitialStateGdl; // to "computeInitialStateGdl" method

	// non-basic methods queries (Prolog methods)
	private static QueryS QUERYsGetRandomMove; // to "getRandomMove" method
	private static QueryAOS QUERYaosGetRandomJointMove1; // to "getRandomJointMove(_)" method
	private static QueryAOS QUERYaosGetRandomJointMove2; // to "getRandomJointMove(_,_,_)" method
	private static QueryAOS QUERYaosGetRandomNextState; // to"getRandomNextState" method
	private static QueryAOS QUERYaosPerformDepthCharge; // to "performDepthCharge" method



	// Time limit of a query
	private static final long WaitForQuery = 500;



	/**
	 * Query to Boolean
	 */
	public class QueryB implements Callable<Boolean>
	{
		private String goal;
		public QueryB(){ goal = "is_terminal"; }

		public void setGoal(String newG){ goal = newG; }

		public String getGoal(){ return goal; }

		@Override
		public Boolean call()
		{
			if(engine.deterministicGoal(goal) == true) return true;
			else return false;
		}

	}



	/**
	 *  Query to String
	 */
	public class QueryS implements Callable<String>
	{
		private String subGoal;
		private static final String answer = "[string(_s)]";
		private static final String endOfGoal = ", _s)";
		private String beginingOfGoal;

		public QueryS(){}
		public QueryS(String g){ beginingOfGoal = g; }

		public void setSubGoal(String newG){ subGoal = newG; }

		public String getGoal(){ return beginingOfGoal+subGoal+endOfGoal; }

		@Override
		public String call()
		{
			return (String) engine.deterministicGoal(beginingOfGoal+subGoal+endOfGoal, answer) [0];
		}

	}



	/**
	 *  Query to ArrayOfString
	 */
	public class QueryAOS implements Callable<String[]>
	{
		private String goal;
		private boolean full;
		private String subGoal;
		private static final String answer = "[_lll]";
		private static final String endOfGoal = ", _l), processList(_l, _ll), ipObjectTemplate('ArrayOfString',_lll,_,[_ll],_)";
		private String beginingOfGoal;

		public QueryAOS(){}
		public QueryAOS(String g)
		{
			beginingOfGoal = g;
			full = false;
		}
		public QueryAOS(boolean b, String g)
		{
			if(b == true)
			{
				goal = g;
				full = b;
			}
		}

		public void setSubGoal(String newG){ subGoal = newG; }

		public String getGoal()
		{
			if(full) return goal;
			else return beginingOfGoal+subGoal+endOfGoal;
		}

		@Override
		public String[] call()
		{
			if(full) return (String[]) engine.deterministicGoal(goal, answer) [0];
			else return (String[]) engine.deterministicGoal(beginingOfGoal+subGoal+endOfGoal, answer) [0];
		}

	}

}

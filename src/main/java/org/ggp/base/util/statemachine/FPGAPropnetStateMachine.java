package org.ggp.base.util.statemachine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.ggp.base.util.gdl.grammar.Gdl;
import org.ggp.base.util.gdl.grammar.GdlSentence;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.placeholders.FPGAPropnetLibrary;
import org.ggp.base.util.placeholders.FpgaInternalMove;
import org.ggp.base.util.placeholders.FpgaInternalState;
import org.ggp.base.util.statemachine.abstractsm.ExplicitAndFpgaStateMachineInterface;
import org.ggp.base.util.statemachine.abstractsm.FpgaStateMachineInterface;
import org.ggp.base.util.statemachine.exceptions.StateMachineInitializationException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.structure.compact.CompactMachineState;
import org.ggp.base.util.statemachine.structure.compact.CompactMove;
import org.ggp.base.util.statemachine.structure.compact.CompactRole;
import org.ggp.base.util.statemachine.structure.explicit.ExplicitMachineState;
import org.ggp.base.util.statemachine.structure.explicit.ExplicitMove;
import org.ggp.base.util.statemachine.structure.explicit.ExplicitRole;
import org.ggp.base.util.statemachine.structure.fpga.FpgaMachineState;
import org.ggp.base.util.statemachine.structure.fpga.FpgaMove;
import org.ggp.base.util.statemachine.structure.fpga.FpgaRole;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import csironi.ggp.course.utils.MyPair;

public class FPGAPropnetStateMachine extends StateMachine implements FpgaStateMachineInterface, ExplicitAndFpgaStateMachineInterface{

	/**
	 * Once the functions of the FPGA library are called on a state we memorize the returned output in the
	 * following variables. This is because this class has multiple functions that only return part of such
	 * output and they are normally called in sequence on the same state. It would be a waste to call the
	 * library every time one of such methods is called, and then throw away part of the output.
	 */
	private FpgaInternalState lastVisitedState;

	/**
	 * Legal moves for each role.
	 */
	private List<List<FpgaInternalMove>> legalMovesPerRole;

	/**
	 * Next state for each joint move.
	 */
	private List<MyPair<FpgaInternalState,List<FpgaInternalMove>>> jointMovesAndNextStates;

	/**
	 * Tells whether lastVisitedState is terminal.
	 */
	private boolean terminal;

	/**
	 * Gives the goals for each role in the lastVisitedState.
	 * Note that this is set to null whenever the lastVisitedState is non-terminal.
	 */
	private List<Double> goals;



	private FPGAPropnetLibrary fpgaPropnetInterface;

	/** Explicit format for the player roles */
	protected List<ExplicitRole> explicitRoles;
    /** The player roles */
    protected List<FpgaRole> roles;
    /** The initial state */
    protected FpgaMachineState initialState;


	public FPGAPropnetStateMachine(Random random, FPGAPropnetLibrary fpgaPropnetInterface){
		super(random);
		this.fpgaPropnetInterface = fpgaPropnetInterface;
	}

    /**
     * Initializes the PropNetStateMachine. You should compute the topological
     * ordering here. Additionally you may compute the initial state here, at
     * your discretion.
     *
     * @throws StateMachineInitializationException
     */
    @Override
    public void initialize(List<Gdl> description, long timeout) throws StateMachineInitializationException {

    	if(this.fpgaPropnetInterface == null){
    		GamerLogger.log("StateMachine", "[FPGAPropnet] State machine initialized with FPGAPropnetInterface set to null. Impossible to reason on the game!");
    		throw new StateMachineInitializationException("Null parameter passed during initialization of the state machine: cannot reason on the game with null FPGAPropnetInterface.");
    	}else{

    		int numRoles = this.fpgaPropnetInterface.fucntion1().getLegalMovesPerRole().length;

    		this.roles = new ArrayList<CompactRole>(numRoles);
    		for(int i = 0; i < numRoles; i++){
    			this.roles.add(new CompactRole(i));
    		}
    		this.initialState = new CompactMachineState(this.fpgaPropnetInterface.function2().clone());
    	}

		this.explicitRoles = new ArrayList<ExplicitRole>();
		this.roles = new ArrayList<FpgaRole>();
		ExplicitRole[] rolesArray = propNet.getRoles();
		for(int i = 0; i < rolesArray.length; i++){
			this.explicitRoles.add(rolesArray[i]);
			this.roles.add(new FpgaRole(i));
		}

    }

	/**
	 * Computes if the state is terminal.
	 * Since the state is not an FpgaMachineState, it is first transformed
	 * into one.
	 *
	 * NOTE that this method has been added only for compatibility with other state
	 * machines, however its performance will be much slower than the corresponding
	 * method for the FpgaMachineState since the state will always have
	 * to be translated first into an FpgaMachineState.
	 *
	 * @state a machine state.
	 * @return true if the state is terminal, false otherwise.
	 */
	@Override
	public boolean isTerminal(ExplicitMachineState state) {
		// This state machine cannot translate the ExplicitMachineState to an FpgaMachineState,
		// so it throws an exception because it cannot compute terminality of an ExplicitMachineState.
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to compute terminality of ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
		// Here we throw a RuntimeException instead of one of the state machine checked exceptions
		// because the problem is not due to an error of the state machine. It means that there is
		// something wrong in the code that should not call this method in the first place.
		throw new RuntimeException("StateMachine - [FPGAPropnet] Impossible to compute terminality of ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
	}

	/**
	 * Computes if the state is terminal. Assume that if function1 returns no legal moves for any of the players
	 * than the state is terminal.
	 *
	 * @state a machine state.
	 * @return true if the state is terminal, false otherwise.
	 */
	@Override
	public boolean isTerminal(FpgaMachineState state) {
		this.getStateInfo(state);

		return this.terminal;
	}

	@Override
	public List<Double> getAllGoalsForOneRole(ExplicitMachineState state, ExplicitRole role) {
		// This state machine cannot translate the ExplicitMachineState to an FpgaMachineState,
		// so it throws an exception because it cannot compute goals in an ExplicitMachineState.
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to compute goals in ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
		throw new RuntimeException("StateMachine - [FPGAPropnet] Impossible to compute goals in ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");

	}

	@Override
	public List<Double> getAllGoalsForOneRole(FpgaMachineState state, FpgaRole role) {
		this.getStateInfo(state);

		List<Double> goalsForRole = new ArrayList<Double>();
		// If the goals are null because the state is non-terminal we return an empty list of goals
		if(this.goals != null) {
			goalsForRole.add(this.goals.get(this.getFpgaRoleIndices().get(role)));
		}

		return goalsForRole;
	}

	@Override
	public ExplicitMachineState getExplicitInitialState() {
		// This state machine cannot translate the FpgaMachineState to an ExplicitMachineState, so it
		// throws an exception because it cannot compute the initial state as an ExplicitMachineState.
		// NOTE: we throw exception here instead of in the method convertToExplicitMachineState() because
		// there are still parts in the code where we don't care if that method doesn't return a correct
		// state translation (e.g. when we just want to log the state the agent's search won't be affected
		// by a wrong state translation).
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to compute terminality of ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
		throw new RuntimeException("StateMachine - [FPGAPropnet] Impossible to compute terminality of ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
	}

	@Override
	public FpgaMachineState getFpgaInitialState() {
		return this.initialState;
	}

	@Override
	public List<ExplicitMove> getExplicitLegalMoves(ExplicitMachineState state, ExplicitRole role) {
		// This state machine cannot translate the ExplicitMachineState to an FpgaMachineState, so it
		// throws an exception because it cannot compute the moves of an ExplicitMachineState.
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to compute legal moves in an ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
		throw new RuntimeException("StateMachine - [FPGAPropnet] Impossible to compute legal moves in an ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");

	}

	@Override
	public List<FpgaMove> getFpgaLegalMoves(FpgaMachineState state, FpgaRole role) {
		this.getStateInfo(state);
		List<FpgaMove> legalMoves = new ArrayList<FpgaMove>();
		if(this.legalMovesPerRole != null) {
			for(FpgaInternalMove move : this.legalMovesPerRole.get(this.getFpgaRoleIndices().get(role))) {
				legalMoves.add(new FpgaMove(move));
			}
		}
		return legalMoves;
	}

	@Override
	public ExplicitMachineState getExplicitNextState(ExplicitMachineState state, List<ExplicitMove> moves) {
		// This state machine cannot translate the ExplicitMachineState to an FpgaMachineState, so it
		// throws an exception because it cannot compute the moves of an ExplicitMachineState.
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to compute the next state for an ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");
		throw new RuntimeException("StateMachine - [FPGAPropnet] Impossible to compute the next state for an ExplicitMachineState, cannot translate to corresponding FpgaMachineState.");

	}

	// TODO: to make node creation more efficient add getAllJointMovesAndNextStates(FpgaMachineState state)
	// to this state machine.
	@Override
	public FpgaMachineState getFpgaNextState(FpgaMachineState state, List<FpgaMove> moves) throws TransitionDefinitionException{
		this.getStateInfo(state);
		List<FpgaInternalMove> fpgaInternalMoves = new ArrayList<FpgaInternalMove>();
		for(FpgaMove move : moves) {
			fpgaInternalMoves.add(move.getInternalMove());
		}
		for(MyPair<FpgaInternalState,List<FpgaInternalMove>> pair : this.jointMovesAndNextStates) {
			if(this.isSameJointMove(pair.getSecond(), fpgaInternalMoves)) {
				return new FpgaMachineState(pair.getFirst());
			}
		}

		// If we are here we didn't find any next state
		throw new TransitionDefinitionException(new ExplicitMachineState(), this.convertToExplicitMoves(moves));

	}

	@Override
	public List<ExplicitRole> getExplicitRoles() {
		return ImmutableList.copyOf(this.explicitRoles);
	}

	@Override
	public List<FpgaRole> getFpgaRoles() {
		return this.roles;
	}

	/*** Methods to convert propnet states/moves/roles to GDL states/moves/roles and vice versa ***/

	/**
	 * For the FPGA propnet we cannot convert states, so we just return null.
	 * We also log an error to signal that this method has been called.
	 */
	@Override
	public ExplicitMachineState convertToExplicitMachineState(FpgaMachineState state) {
		GamerLogger.logError("StateMachine", "[FPGAPropnet] Impossible to convert FpgaMachineState to ExplicitMachineState.");
		return new ExplicitMachineState(new HashSet<GdlSentence>());
	}

	@Override
	public ExplicitMove convertToExplicitMove(FpgaMove move) {
		// TODO Auto-generated method stub
		return null;
	}

	public List<ExplicitMove> convertToExplicitMoves(List<FpgaMove> moves) {
		List<ExplicitMove> explicitMoves = new ArrayList<ExplicitMove>();
		for(FpgaMove move : moves) {
			explicitMoves.add(this.convertToExplicitMove(move));
		}
		return explicitMoves;
	}

	@Override
	public ExplicitRole convertToExplicitRole(CompactRole role) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompactMachineState convertToCompactMachineState(ExplicitMachineState state) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public FpgaMove convertToFpgaMove(ExplicitMove move) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CompactRole convertToCompactRole(ExplicitRole role) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public List<CompactMove> movesToInternalMoves(List<ExplicitMove> moves) {
		// TODO  Auto-generated method stub
		return null;
	}

	private void getStateInfo(FpgaMachineState state) {
		if(!state.getStateRepresentation().equals(this.lastVisitedState)) {
			MyPair<List<List<FpgaInternalMove>>,List<MyPair<FpgaInternalState,List<FpgaInternalMove>>>> stateInfo = this.fpgaPropnetInterface.getNextStates(state.getStateRepresentation());
			// TODO: if the state is terminal what does the getNextStates() function return? Should I check if stateInfo is null?
			this.legalMovesPerRole = stateInfo.left;
			this.jointMovesAndNextStates = stateInfo.right;
			this.terminal = this.jointMovesAndNextStates.isEmpty();// TODO: check! If there are no next states will this be null? Empty?
			if(this.terminal) {
				// Assume that on terminal states this method returns the goals in the state and does not
				// perform any simulation, so the value of simulationsNumber is ignored.
				this.goals = this.performSimulations(state.getStateRepresentation(), 1);
			}else {
				this.goals = null;
			}
		}
	}


	private List<Double> performSimulations(FpgaInternalState state, int simulationsNumber) {
		List<Double> scoreSums = this.fpgaPropnetInterface.getScores(state, simulationsNumber);
		List<Double> avgScores = new ArrayList<Double>();
		for(Double d : scoreSums) {
			avgScores.add(new Double(d.doubleValue()/simulationsNumber));
		}
		return avgScores;
	}

    private Map<FpgaRole,Integer> fpgaRoleIndices = null;
    /**
     * Returns a mapping from a role to the index of that role, as in
     * the list returned by {@link #getFpgaRoles()}. This may be a faster
     * way to check the index of a role than calling {@link List#indexOf(Object)}
     * on that list.
     */
    public Map<FpgaRole, Integer> getFpgaRoleIndices()
    {
        if (fpgaRoleIndices == null) {
        	ImmutableMap.Builder<FpgaRole, Integer> roleIndicesBuilder = ImmutableMap.builder();
            List<FpgaRole> roles = getFpgaRoles();
            for (int i = 0; i < roles.size(); i++) {
                roleIndicesBuilder.put(roles.get(i), i);
            }
            fpgaRoleIndices = roleIndicesBuilder.build();
        }

        return fpgaRoleIndices;
    }

	@Override
	public void shutdown() {
		// Do nothing
	}


}

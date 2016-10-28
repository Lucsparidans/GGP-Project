package org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.playout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.playout.jointmoveselector.JointMoveSelector;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.SimulationResult;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.StateMachineException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.structure.MachineState;
import org.ggp.base.util.statemachine.structure.Move;
import org.ggp.base.util.statemachinenew.AbstractStateMachine;

public class MovesMemorizingStandardPlayout extends StandardPlayout{

	public MovesMemorizingStandardPlayout(AbstractStateMachine theMachine,
			JointMoveSelector jointMoveSelector) {
		super(theMachine, jointMoveSelector);

	}

	@Override
	public SimulationResult playout(MachineState state, int maxDepth) {

		// NOTE that this is just an extra check: if the state is terminal or the depth limit has been reached,
		// we just return the final goals of the state. At the moment the MCTS manager already doesn't call the
        // play-out if the state is terminal or if the depth limit has been reached, so this check will never be
        // true, but it's here just to be safe.
        boolean terminal = true;

        try {
			terminal = this.theMachine.isTerminal(state);
		} catch (StateMachineException e) {
			GamerLogger.logError("MCTSManager", "Exception computing state terminality while performing a playout.");
			GamerLogger.logStackTrace("MCTSManager", e);
			terminal = true;
		}

		if(terminal || maxDepth == 0){

			GamerLogger.logError("MCTSManager", "Playout strategy shouldn't be called on a terminal node. The MCTSManager must take care of computing the simulation result in this case.");

			return new SimulationResult(0, this.theMachine.getSafeGoalsAvgForAllRoles(state));

		}

        int nDepth = 0;

        List<List<Move>> allJointMoves = new ArrayList<List<Move>>();

        List<Move> jointMove;

        do{ // NOTE: if any of the try blocks fails on the first iteration this method will return a result with only the terminal goals of the starting state of the playout, depth 0 and empty moves list

        	jointMove = null;
			try {
				jointMove = this.jointMoveSelector.getJointMove(state);
			} catch (MoveDefinitionException | StateMachineException e) {
				GamerLogger.logError("MCTSManager", "Exception getting a joint move while performing a playout.");
				GamerLogger.logStackTrace("MCTSManager", e);
				break;
			}
			try {
				state = this.theMachine.getNextState(state, jointMove);
			} catch (TransitionDefinitionException | StateMachineException e) {
				GamerLogger.logError("MCTSManager", "Exception getting the next state while performing a playout.");
				GamerLogger.logStackTrace("MCTSManager", e);
				break;
			}

			allJointMoves.add(jointMove);

			nDepth++;

            try {
				terminal = this.theMachine.isTerminal(state);
			} catch (StateMachineException e) {
				GamerLogger.logError("MCTSManager", "Exception computing state terminality while performing a playout.");
				GamerLogger.logStackTrace("MCTSManager", e);
				terminal = true;
				break;
			}

        }while(nDepth < maxDepth && !terminal);

        Collections.reverse(allJointMoves);

        return new SimulationResult(nDepth, this.theMachine.getSafeGoalsAvgForAllRoles(state), allJointMoves);

	}

}
package org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.amafdecoupled;

import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.decoupled.DecoupledMCTSMoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.decoupled.DecoupledTreeNodeFactory;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.statemachine.abstractsm.AbstractStateMachine;
import org.ggp.base.util.statemachine.exceptions.StateMachineException;
import org.ggp.base.util.statemachine.structure.MachineState;

public class AMAFDecoupledTreeNodeFactory extends DecoupledTreeNodeFactory {

	public AMAFDecoupledTreeNodeFactory(AbstractStateMachine theMachine) {
		super(theMachine);
	}

	@Override
	public MCTSNode createNewNode(MachineState state) {

		//System.out.println("Creating new node.");

		int goals[] = null;
		boolean terminal = false;

		DecoupledMCTSMoveStats[][] ductMovesStats = null;

		try {
			terminal = this.theMachine.isTerminal(state);
		} catch (StateMachineException e) {
			GamerLogger.logError("MCTSManager", "Failed to compute state terminality when creating new tree node. Considering node terminal.");
			GamerLogger.logStackTrace("MCTSManager", e);
			terminal = true;
		}

		// Terminal state:
		if(terminal){

			goals = this.theMachine.getSafeGoalsAvgForAllRoles(state);
			terminal = true;

		}else{// Non-terminal state:

			ductMovesStats = this.createDUCTMCTSMoves(state);

			// Error when computing moves.
			// If for at least one player the legal moves cannot be computed (an thus the moves
			// are returned as a null value), we consider this node "pseudo-terminal" (i.e. the
			// corresponding state is not terminal but we cannot explore any of the next states,
			// so we treat it as terminal during the MCT search). This means that we will need
			// the goal values in this node and they will not change for all the rest of the
			// search, so we compute them and memorize them.
			if(ductMovesStats == null){
				// Compute the goals for each player. We are in a non terminal state so the goal might not be defined.
				// We use the state machine method that will return default goal values for the player for which goal
				// values cannot be computed in this state.
				goals = this.theMachine.getSafeGoalsAvgForAllRoles(state);
				terminal = true;
			}
			// If the legal moves can be computed for every player, there is no need to compute the goals.
		}

		return new AMAFDecoupledMCTSNode(ductMovesStats, goals, terminal);
	}

}

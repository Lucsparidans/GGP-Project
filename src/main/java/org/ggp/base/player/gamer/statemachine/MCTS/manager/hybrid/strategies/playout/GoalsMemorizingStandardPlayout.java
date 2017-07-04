package org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.playout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.statemachine.GamerSettings;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.GameDependentParameters;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.SharedReferencesCollector;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.SimulationResult;
import org.ggp.base.util.logging.GamerLogger;
import org.ggp.base.util.statemachine.exceptions.MoveDefinitionException;
import org.ggp.base.util.statemachine.exceptions.StateMachineException;
import org.ggp.base.util.statemachine.exceptions.TransitionDefinitionException;
import org.ggp.base.util.statemachine.structure.MachineState;
import org.ggp.base.util.statemachine.structure.Move;

public class GoalsMemorizingStandardPlayout extends StandardPlayout{

	public GoalsMemorizingStandardPlayout(GameDependentParameters gameDependentParameters, Random random,
			GamerSettings gamerSettings, SharedReferencesCollector sharedReferencesCollector, String id){
		super(gameDependentParameters, random, gamerSettings, sharedReferencesCollector, id);
	}

	@Override
	public SimulationResult singlePlayout(MachineState state, int maxDepth) {

		// NOTE that this is just an extra check: if the state is terminal or the depth limit has been reached,
		// we just return the final goals of the state. At the moment the MCTS manager already doesn't call the
        // play-out if the state is terminal or if the depth limit has been reached, so this check will never be
        // true, but it's here just to be safe.
	    boolean terminal = true;

	    try {
	    	terminal = this.gameDependentParameters.getTheMachine().isTerminal(state);
	    } catch (StateMachineException e) {
	    	GamerLogger.logError("MctsManager", "Exception computing state terminality while performing a playout.");
			GamerLogger.logStackTrace("MctsManager", e);
			terminal = true;
		}

		if(terminal || maxDepth == 0){

			GamerLogger.logError("MctsManager", "Playout strategy shouldn't be called on a terminal node. The MctsManager must take care of computing the simulation result in this case.");

			return new SimulationResult(0, this.gameDependentParameters.getTheMachine().getSafeGoalsAvgForAllRoles(state));

		}

        int nDepth = 0;

        List<int[]> allGoals = new ArrayList<int[]>();

        List<Move> jointMove;

        allGoals.add(this.gameDependentParameters.getTheMachine().getSafeGoalsAvgForAllRoles(state));

        do{ // NOTE: if any of the try blocks fails on the first iteration this method will return a result with only the terminal goals of the starting state of the playout, depth 0 and empty moves list

        	jointMove = null;
			try {
				jointMove = this.moveSelector.getJointMove(state);
			} catch (MoveDefinitionException | StateMachineException e) {
				GamerLogger.logError("MctsManager", "Exception getting a joint move while performing a playout.");
				GamerLogger.logStackTrace("MctsManager", e);
				break;
			}
			try {
				state = this.gameDependentParameters.getTheMachine().getNextState(state, jointMove);
			} catch (TransitionDefinitionException | StateMachineException e) {
				GamerLogger.logError("MctsManager", "Exception getting the next state while performing a playout.");
				GamerLogger.logStackTrace("MctsManager", e);
				break;
			}

			allGoals.add(this.gameDependentParameters.getTheMachine().getSafeGoalsAvgForAllRoles(state));

			nDepth++;

            try {
				terminal = this.gameDependentParameters.getTheMachine().isTerminal(state);
			} catch (StateMachineException e) {
				GamerLogger.logError("MctsManager", "Exception computing state terminality while performing a playout.");
				GamerLogger.logStackTrace("MctsManager", e);
				terminal = true;
				break;
			}

        }while(nDepth < maxDepth && !terminal);

        Collections.reverse(allGoals);

        return new SimulationResult(nDepth, allGoals);

	}

}

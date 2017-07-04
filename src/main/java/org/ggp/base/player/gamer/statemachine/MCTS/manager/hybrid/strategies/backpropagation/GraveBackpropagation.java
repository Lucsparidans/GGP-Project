package org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.backpropagation;

import java.util.Random;

import org.ggp.base.player.gamer.statemachine.GamerSettings;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.GameDependentParameters;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.SharedReferencesCollector;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.backpropagation.nodeupdaters.GraveUpdater;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.backpropagation.nodeupdaters.StandardUpdater;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MctsNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.MctsJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.SimulationResult;
import org.ggp.base.util.statemachine.structure.MachineState;

public class GraveBackpropagation extends BackpropagationStrategy {

	private StandardUpdater standardUpdater;

	private GraveUpdater graveUpdater;

	public GraveBackpropagation(GameDependentParameters gameDependentParameters, Random random,
			GamerSettings gamerSettings, SharedReferencesCollector sharedReferencesCollector) {

		super(gameDependentParameters, random, gamerSettings, sharedReferencesCollector);

		this.standardUpdater = new StandardUpdater(gameDependentParameters, random, gamerSettings, sharedReferencesCollector);
		this.graveUpdater = new GraveUpdater(gameDependentParameters, random, gamerSettings, sharedReferencesCollector);
	}

	@Override
	public void setReferences(SharedReferencesCollector sharedReferencesCollector) {
		this.standardUpdater.setReferences(sharedReferencesCollector);
		this.graveUpdater.setReferences(sharedReferencesCollector);
	}

	@Override
	public void clearComponent() {
		this.standardUpdater.clearComponent();
		this.graveUpdater.clearComponent();
	}

	@Override
	public void setUpComponent() {
		this.standardUpdater.setUpComponent();
		this.graveUpdater.setUpComponent();
	}


	@Override
	public void update(MctsNode currentNode, MachineState currentState, MctsJointMove jointMove, SimulationResult[] simulationResult) {

		this.standardUpdater.update(currentNode, currentState, jointMove, simulationResult);
		this.graveUpdater.update(currentNode, currentState, jointMove, simulationResult);

	}

	@Override
	public void processPlayoutResult(MctsNode leafNode, MachineState leafState, SimulationResult[] simulationResult) {

		this.graveUpdater.processPlayoutResult(leafNode, leafState, simulationResult);

	}

	@Override
	public String getComponentParameters(String indentation) {
		return indentation + "NODE_UPDATER_1 = " + this.standardUpdater.printComponent(indentation + "  ") + indentation + "NODE_UPDATER_2 = " + this.graveUpdater.printComponent(indentation + "  ");
	}

}

package org.ggp.base.player.gamer.statemachine.MCTS.propnet.ravegrave;

import java.util.Random;

import org.ggp.base.player.gamer.statemachine.MCTS.manager.evolution.Individual;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.evolution.SingleParameterEvolutionManager;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.InternalPropnetMCTSManager;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftermove.PnEvoAfterMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftersimulation.PnEvoAfterSimulation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftersimulation.PnEvoGRAVEAfterSimulation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftersimulation.PnGRAVEAfterSimulation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.backpropagation.PnGRAVEBackpropagation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.beforesimulation.PnEvoBeforeSimulation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.expansion.PnNoExpansion;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.movechoice.PnMaximumScoreChoice;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.playout.PnGRAVEPlayout;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.selection.PnGRAVESelection;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.selection.evaluators.GRAVE.PnGRAVEEvaluator;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.prover.ProverMCTSManager;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.amafdecoupled.PnAMAFDecoupledTreeNodeFactory;
import org.ggp.base.util.statemachine.structure.compact.CompactRole;

public class BTunerRaveDuctMctsGamer extends RaveDuctMctsGamer {

	protected double evoC;

	protected double evoValueOffset;

	protected double[] individualsValues;

	public BTunerRaveDuctMctsGamer() {

		super();

		this.evoC = 0.05;

		this.evoValueOffset = 0.01;

		this.individualsValues = new double[8];

		this.individualsValues[0] = 1000;
		this.individualsValues[1] = 100;
		this.individualsValues[2] = 10;
		this.individualsValues[3] = 1;
		this.individualsValues[4] = 0.1;
		this.individualsValues[5] = 0.01;
		this.individualsValues[6] = 0.001;
		this.individualsValues[7] = 0.0001;

	}

	@Override
	public InternalPropnetMCTSManager createPropnetMCTSManager() {

		Random r = new Random();

		CompactRole myRole = this.thePropnetMachine.roleToInternalRole(this.getRole());
		int numRoles = this.thePropnetMachine.getInternalRoles().length;

		PnGRAVESelection graveSelection = new PnGRAVESelection(numRoles, myRole, r, this.valueOffset, this.minAMAFVisits, new PnGRAVEEvaluator(this.c, this.unexploredMoveDefaultSelectionValue, this.betaComputer, this.defaultExploration));

		Individual[] individuals = new Individual[this.individualsValues.length];

		for(int i = 0; i < this.individualsValues.length; i++){
			individuals[i] = new Individual(this.individualsValues[i]);
		}

		SingleParameterEvolutionManager evolutionManager = new SingleParameterEvolutionManager(r, this.evoC, this.evoValueOffset, individuals);

		return new InternalPropnetMCTSManager(graveSelection, new PnNoExpansion() /*new RandomExpansion(numRoles, myRole, r)*/,
				new PnGRAVEPlayout(this.thePropnetMachine), new PnGRAVEBackpropagation(numRoles, myRole), new PnMaximumScoreChoice(myRole, r),
				new PnEvoBeforeSimulation(evolutionManager, this.betaComputer),
				new PnEvoGRAVEAfterSimulation(new PnGRAVEAfterSimulation(graveSelection), new PnEvoAfterSimulation(evolutionManager, myRole)),
				new PnEvoAfterMove(evolutionManager), new PnAMAFDecoupledTreeNodeFactory(this.thePropnetMachine),
				this.thePropnetMachine, this.gameStepOffset, this.maxSearchDepth, this.logTranspositionTable);

	}

	@Override
	public ProverMCTSManager createProverMCTSManager(){

		/*
		Random r = new Random();

		Role myRole = this.getRole();
		int numRoles = this.getStateMachine().getRoles().size();

		int myRoleIndex = this.getStateMachine().getRoleIndices().get(this.getRole());

		List<List<Move>> allJointMoves = new ArrayList<List<Move>>();

		ProverGRAVESelection graveSelection = new ProverGRAVESelection(numRoles, myRole, r, this.valueOffset, this.minAMAFVisits, new ProverGRAVEEvaluator(this.c, this.unexploredMoveDefaultSelectionValue, this.betaComputer, this.defaultExploration));

		ProverGRAVEPlayout gravePlayout = new ProverGRAVEPlayout(this.getStateMachine(), allJointMoves);

		return new ProverMCTSManager(graveSelection, new ProverNoExpansion() /*new RandomExpansion(numRoles, myRole, r)*//*,
				gravePlayout, new ProverGRAVEBackpropagation(numRoles, myRole, allJointMoves),
				new ProverMaximumScoreChoice(myRoleIndex, r), new ProverGRAVEAfterSimulation(graveSelection, gravePlayout),
				null, new ProverAMAFDecoupledTreeNodeFactory(this.getStateMachine()), this.getStateMachine(),
	       		this.gameStepOffset, this.maxSearchDepth);

	    */

		return null;
	}

}
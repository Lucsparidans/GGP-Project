package org.ggp.base.player.gamer.statemachine.MCTS.propnet.ravegrave;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

import org.ggp.base.player.gamer.statemachine.MCS.manager.MoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.InternalPropnetMCTSManager;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftermove.MASTAfterMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftermove.PhMASTAfterMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftermove.ProgressiveHistoryAfterMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.aftersimulation.GRAVEAfterSimulation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.backpropagation.MASTGRAVEBackpropagation;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.expansion.NoExpansion;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.movechoice.MaximumScoreChoice;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.playout.MASTPlayout;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.selection.ProgressiveHistoryGRAVESelection;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.selection.evaluators.GRAVE.ProgressiveHistoryGRAVEEvaluator;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.treestructure.AMAFDecoupled.PnAMAFDecoupledTreeNodeFactory;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.prover.ProverMCTSManager;
import org.ggp.base.util.statemachine.inernalPropnetStructure.InternalPropnetMove;
import org.ggp.base.util.statemachine.inernalPropnetStructure.InternalPropnetRole;

public abstract class PhGRMastDuctMctsGamer extends GRMastDuctMctsGamer {

	protected double w;

	public PhGRMastDuctMctsGamer() {

		super();

		this.w = 5.0;

	}

	@Override
	public InternalPropnetMCTSManager createPropnetMCTSManager() {

		Random r = new Random();

		InternalPropnetRole myRole = this.thePropnetMachine.roleToInternalRole(this.getRole());
		int numRoles = this.thePropnetMachine.getInternalRoles().length;

		ProgressiveHistoryGRAVESelection graveSelection = new ProgressiveHistoryGRAVESelection(numRoles, myRole, r, this.valueOffset, this.minAMAFVisits, new ProgressiveHistoryGRAVEEvaluator(this.c, this.unexploredMoveDefaultSelectionValue, this.betaComputer, this.defaultExploration, this.w));

		Map<InternalPropnetMove, MoveStats> mastStatistics = new HashMap<InternalPropnetMove, MoveStats>();

		// Note that the after simulation strategy GRAVEAfterSimulation already performs all the after simulation
		// actions needed by the MAST strategy, so we don't need to change it when we use GRAVE and MAST together.
		return new InternalPropnetMCTSManager(graveSelection, new NoExpansion() /*new RandomExpansion(numRoles, myRole, r)*/,
				new MASTPlayout(this.thePropnetMachine, r, mastStatistics, this.epsilon), new MASTGRAVEBackpropagation(numRoles, myRole, mastStatistics),
				new MaximumScoreChoice(myRole, r), null, new GRAVEAfterSimulation(graveSelection),
				new PhMASTAfterMove(new MASTAfterMove(mastStatistics, this.decayFactor), new ProgressiveHistoryAfterMove(graveSelection)),
				new PnAMAFDecoupledTreeNodeFactory(this.thePropnetMachine),
				this.thePropnetMachine,	this.gameStepOffset, this.maxSearchDepth, this.logTranspositionTable);
	}

	@Override
	public ProverMCTSManager createProverMCTSManager(){


		// ZZZ!

		return null;


	}

}

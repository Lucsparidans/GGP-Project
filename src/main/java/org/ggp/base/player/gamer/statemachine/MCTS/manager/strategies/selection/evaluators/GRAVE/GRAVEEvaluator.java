package org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.selection.evaluators.GRAVE;

import java.util.Map;

import org.ggp.base.player.gamer.statemachine.MCS.manager.MoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.selection.evaluators.UCTEvaluator;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.PnMCTSNode;
import org.ggp.base.util.statemachine.inernalPropnetStructure.InternalPropnetMove;

public class GRAVEEvaluator extends UCTEvaluator {

	/**
	 * Reference to the AMAF statistics of the ancestor of this node that has enough visits
	 * to make the statistics reliable. This reference will be updated every time the current
	 * node being checked has enough visits to use its own AMAF statistics.
	 */
	private Map<InternalPropnetMove, MoveStats> amafStats;

	private BetaComputer betaComputer;

	public GRAVEEvaluator(double c, double defaultValue, BetaComputer betaComputer) {
		super(c, defaultValue);
		this.betaComputer = betaComputer;
		this.amafStats = null;
	}

	@Override
	protected double computeExploitation(PnMCTSNode theNode, InternalPropnetMove theMove, MoveStats theMoveStats){

		double uctExploitation = super.computeExploitation(theNode, theMove, theMoveStats);

		double amafExploitation = -1.0;

		MoveStats moveAmafStats = null;

		if(this.amafStats != null){

			moveAmafStats = this.amafStats.get(theMove);

			if(moveAmafStats != null && moveAmafStats.getVisits() != 0){
				amafExploitation = (moveAmafStats.getScoreSum() / moveAmafStats.getVisits()) / 100.0;
			}

		}

		if(uctExploitation == -1){
			return amafExploitation;
		}

		if(amafExploitation == -1){
			return uctExploitation;
		}

		double beta = this.betaComputer.computeBeta(theMoveStats, moveAmafStats, theNode.getTotVisits());

		//System.out.println("uct = " + uct);
		//System.out.println("amafAvg = " + amafAvg);
		//System.out.println("beta = " + beta);

		//System.out.println("returning = " + (((1.0 - beta) * uct) + (beta * amafAvg)));

		if(beta == -1){
			return -1.0;
		}else{
			return (((1.0 - beta) * uctExploitation) + (beta * amafExploitation));
		}

	}

	@Override
	protected double computeExploration(PnMCTSNode theNode, MoveStats theMoveStats){

		double exploration = super.computeExploration(theNode, theMoveStats);

		if(exploration != -1){
			return exploration;
		}else{
			return 100.0;
		}

	}

	public void setAmafStats(Map<InternalPropnetMove, MoveStats> amafStats){
		this.amafStats = amafStats;
	}

	public Map<InternalPropnetMove, MoveStats> getAmafStats(){
		return this.amafStats;
	}

	@Override
	public String getEvaluatorParameters() {
		String params = super.getEvaluatorParameters();

		if(params != null){
			return params + ", " + this.betaComputer.printBetaComputer();
		}else{
			return this.betaComputer.printBetaComputer();
		}
	}

}

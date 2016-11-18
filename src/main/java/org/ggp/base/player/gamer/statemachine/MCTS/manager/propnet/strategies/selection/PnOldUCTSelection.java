package org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.strategies.selection;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MctsNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.PnMCTSJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.PnSequDecMCTSJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.decoupled.PnDecoupledMCTSMoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.decoupled.PnDecoupledMCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.sequential.PnSequentialMCTSMoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.sequential.PnSequentialMCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.slowsequential.PnSlowSeqentialMCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.slowsequential.PnSlowSequentialMCTSJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.propnet.slowsequential.PnSlowSequentialMCTSMoveStats;
import org.ggp.base.util.statemachine.structure.compact.CompactMove;
import org.ggp.base.util.statemachine.structure.compact.CompactRole;

/**
 * Old version of the structure of the UCT selection strategy.
 * Substituted by the new version implemented by class UCTSelection.
 * (New version works exactly in the same way, only the structure
 * of the code changed).
 *
 * @author C.Sironi
 *
 */
public class PnOldUCTSelection implements PnSelectionStrategy {

	/**
	 * The total number of roles in the game.
	 * Needed by the SUCT version of MCTS.
	 */
	private int numRoles;

	/**
	 * The role that is actually performing the search.
	 * Needed by the SUCT version of MCTS.
	 */
	private CompactRole myRole;

	private Random random;

	private double uctOffset;

	private double c;

	public PnOldUCTSelection(int numRoles, CompactRole myRole, Random random, double uctOffset, double c) {
		this.numRoles = numRoles;
		this.myRole = myRole;
		this.random = random;
		this.uctOffset = uctOffset;
		this.c = c;
	}

	/*
	 * (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.MCTS.manager.strategies.selection.SelectionStrategy#select(org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.InternalPropnetMCTSNode)
	 */
	@Override
	public PnMCTSJointMove select(MctsNode currentNode) {
		if(currentNode instanceof PnDecoupledMCTSNode){
			return this.ductSelect((PnDecoupledMCTSNode)currentNode);
		}else if(currentNode instanceof PnSequentialMCTSNode){
			return this.suctSelect((PnSequentialMCTSNode)currentNode);
		}else if(currentNode instanceof PnSlowSeqentialMCTSNode){
			return this.ssuctSelect((PnSlowSeqentialMCTSNode)currentNode);
		}else{
			throw new RuntimeException("UCTSelection-select(): detected a node of a non-recognizable sub-type of class InternalPropnetMCTreeNode.");
		}
	}

	private PnMCTSJointMove ductSelect(PnDecoupledMCTSNode currentNode) {

		/* No need for this check, if the code is correct, because the node that is passed as input
		 * is always non-terminal.
		if(currentNode.isTerminal()){
			GamerLogger.logError("MCTSManager", "Trying to perform selection on a terminal node.");
			throw new RuntimeException("Trying to perform selection on a treminal node.");
		}
		*/

		PnDecoupledMCTSMoveStats[][] moves = currentNode.getMoves();

		/* Also here we can assume that the moves will be non-null since the code takes care of only passing to
		 * this method the nodes that have all the information needed for selection.
		if(moves == null){
			GamerLogger.logError("MCTSManager", "Trying to perform selection on a node with no legal moves.");
			throw new RuntimeException("Trying to perform selection on a node with no legal moves.");
		}
		*/

		List<CompactMove> selectedJointMove = new ArrayList<CompactMove>();
		int[] movesIndices = new int[moves.length];

		double maxUCTvalue;
		double[] UCTvalues;

		int nodeVisits = currentNode.getTotVisits();

		// For each role check the statistics and pick a move.
		for(int i = 0; i < moves.length; i++){

			// Compute UCT value for all moves.
			maxUCTvalue = -1;
			UCTvalues = new double[moves[i].length];

			// For each legal move check the UCTvalue.
			for(int j = 0; j < moves[i].length; j++){

				// Compute the UCT value.
				UCTvalues[j] = this.computeUCTvalue(moves[i][j].getScoreSum(), (double) moves[i][j].getVisits(), (double) nodeVisits);

				// If it's higher than the current maximum one, replace the max value.
				if(UCTvalues[j] > maxUCTvalue){
					maxUCTvalue = UCTvalues[j];
				}
			}

			// Now that we have the maximum UCT value we can look for all moves that have their UCT value
			// in the interval [maxUCTvalue-offset, maxUCTvalue].
			List<Integer> selectedMovesIndices = new ArrayList<Integer>();

			for(int j = 0; j < UCTvalues.length; j++){
				if(UCTvalues[j] >= (maxUCTvalue-this.uctOffset)){
					selectedMovesIndices.add(new Integer(j));
				}
			}

			// Extra check (should never be true).
			if(selectedMovesIndices.isEmpty()){
				throw new RuntimeException("DUCT selection: detected no moves with UCT value higher than -1.");
			}

			/*
			if(selectedMovesIndices.size() < 1){
				System.out.println();
				System.out.println();
				System.out.println("!!!");
				System.out.println("Analyzing role " + i + ".");
				System.out.println("Moves for role: " + moves[i].length);
				System.out.println("MaxUCT: " + maxUCTvalue);
				System.out.println("UCTOffset: " + this.uctOffset);
				System.out.println("C constant: " + this.c);
				System.out.println("THE NODE:");
				System.out.println(currentNode);
				System.out.println();
				System.out.println();
			}
			*/

			movesIndices[i] = selectedMovesIndices.get(this.random.nextInt(selectedMovesIndices.size())).intValue();
			selectedJointMove.add(moves[i][movesIndices[i]].getTheMove());
		}

		return new PnSequDecMCTSJointMove(selectedJointMove, movesIndices);
	}

	private PnMCTSJointMove suctSelect(PnSequentialMCTSNode currentNode){

		List<CompactMove> jointMove = new ArrayList<CompactMove>(this.numRoles);
		int[] movesIndices = new int[this.numRoles];

		// Initialize ArrayList with numRoles null elements.
		for(int i = 0; i < this.numRoles; i++){
			jointMove.add(null);
		}

		// Get the index of myRole.
		int roleIndex = this.myRole.getIndex();

		// Get the moves for myRole.
		PnSequentialMCTSMoveStats[] movesStats = currentNode.getMovesStats();

		//SUCTMCTSMoveStats chosenMove = null;

		double maxUCTvalue;
		double[] UCTvalues;
		int nodeVisits = currentNode.getTotVisits();

		while(movesStats != null){

			// Compute UCT value for all moves.
			maxUCTvalue = -1;
			UCTvalues = new double[movesStats.length];

			for(int i = 0; i < movesStats.length; i++){
				// Compute the UCT value.
				UCTvalues[i] = this.computeUCTvalue(movesStats[i].getScoreSum(), (double) movesStats[i].getVisits(), (double) nodeVisits);

				//movesStats[i].setUct(UCTvalue);

				// If it's higher than the current maximum one, replace the max value
				if(UCTvalues[i] > maxUCTvalue){
					maxUCTvalue = UCTvalues[i];
				}
			}

			// Now that we have the maximum UCT value we can look for all moves that have their UCT value
			// in the interval [maxUCTvalue-offset, maxUCTvalue].
			List<Integer> selectedMovesIndices = new ArrayList<Integer>();

			for(int i = 0; i < UCTvalues.length; i++){
				if(UCTvalues[i] >= (maxUCTvalue-this.uctOffset)){
					selectedMovesIndices.add(new Integer(i));
				}
			}

			// Extra check (should never be true).
			if(selectedMovesIndices.isEmpty()){
				throw new RuntimeException("SUCT selection: detected no moves with UCT value higher than -1.");
			}

			// Add one of the selected moves to the joint move.
			movesIndices[roleIndex] = selectedMovesIndices.get(this.random.nextInt(selectedMovesIndices.size())).intValue();
			jointMove.set(roleIndex, currentNode.getAllLegalMoves().get(roleIndex).get(movesIndices[roleIndex]));

			// Get the move statistics of the next role, given the selected move.
			movesStats = movesStats[movesIndices[roleIndex]].getNextRoleMovesStats();

			// Compute the index for the next role
			roleIndex = (roleIndex+1)%this.numRoles;

		}

		return new PnSequDecMCTSJointMove(jointMove, movesIndices);

	}

	private PnMCTSJointMove ssuctSelect(PnSlowSeqentialMCTSNode currentNode){

		List<CompactMove> jointMove = new ArrayList<CompactMove>(this.numRoles);

		// Initialize ArrayList with numRoles null elements.
		for(int i = 0; i < this.numRoles; i++){
			jointMove.add(null);
		}

		// Get the index of myRole.
		int roleIndex = this.myRole.getIndex();

		// Get the moves for myRole.
		PnSlowSequentialMCTSMoveStats[] movesStats = currentNode.getMovesStats();

		PnSlowSequentialMCTSMoveStats chosenMove = null;

		double maxUCTvalue;
		double UCTvalues[];
		int nodeVisits = currentNode.getTotVisits();

		while(movesStats != null){

			// Compute UCT value for all moves.
			maxUCTvalue = -1;
			UCTvalues = new double[movesStats.length];

			for(int i = 0; i < movesStats.length; i++){
				// Compute the UCT value.
				UCTvalues[i] = this.computeUCTvalue(movesStats[i].getScoreSum(), (double) movesStats[i].getVisits(), (double) nodeVisits);

				//movesStats[i].setUct(UCTvalue);

				// If it's higher than the current maximum one, replace the max value
				if(UCTvalues[i] > maxUCTvalue){
					maxUCTvalue = UCTvalues[i];
				}
			}

			// Now that we have the maximum UCT value we can look for all moves that have their UCT value
			// in the interval [maxUCTvalue-offset, maxUCTvalue].
			List<Integer> selectedMovesIndices = new ArrayList<Integer>();

			for(int i = 0; i < UCTvalues.length; i++){
				if(UCTvalues[i] >= (maxUCTvalue-this.uctOffset)){
					selectedMovesIndices.add(new Integer(i));
				}
			}

			// Extra check (should never be true).
			if(selectedMovesIndices.isEmpty()){
				throw new RuntimeException("SUCT selection: detected no moves with UCT value higher than -1.");
			}

			// Add one of the selected moves to the joint move.
			int selectedMoveIndex = selectedMovesIndices.get(this.random.nextInt(selectedMovesIndices.size())).intValue();
			chosenMove = movesStats[selectedMoveIndex];
			jointMove.set(roleIndex, chosenMove.getTheMove());

			// Get the move statistics of the next role, given the selected move.
			movesStats = chosenMove.getNextRoleMovesStats();

			// Compute the index for the next role
			roleIndex = (roleIndex+1)%this.numRoles;

		}

		return new PnSlowSequentialMCTSJointMove(jointMove, chosenMove);

	}

	private double computeUCTvalue(double score, double moveVisits, double nodeVisits){

		// NOTE: this should never happen if we use this class together with the InternalPropnetMCTSManager
		// because the selection phase in a node starts only after all moves have been expanded and visited
		// at least once. However a check is performed to keep the computation consistent even when a move
		// has never been visited (i.e. the "infinite" value (Double.MAX_VALUE) is returned).
		if(moveVisits == 0){
			return Double.MAX_VALUE;
		}

		double avgScore = (score / moveVisits) / 100.0;
		double exploration = this.c * (Math.sqrt(Math.log(nodeVisits)/moveVisits));
		return avgScore + exploration;

	}

	@Override
	public String getStrategyParameters() {
		return "UCT_OFFSET = " + this.uctOffset + ", C_CONSTANT = " + this.c;
	}

	@Override
	public String printStrategy() {
		String params = this.getStrategyParameters();

		if(params != null){
			return "[SELECTION_STRATEGY = " + this.getClass().getSimpleName() + ", " + params + "]";
		}else{
			return "[SELECTION_STRATEGY = " + this.getClass().getSimpleName() + "]";
		}
	}

}

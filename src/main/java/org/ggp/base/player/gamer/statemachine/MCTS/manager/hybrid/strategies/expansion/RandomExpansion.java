package org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.expansion;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.GameDependentParameters;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.MCTSJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.SequDecMCTSJointMove;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.decoupled.DecoupledMCTSMoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.decoupled.DecoupledMCTSNode;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.sequential.SequentialMCTSMoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.hybrid.sequential.SequentialMCTSNode;
import org.ggp.base.util.statemachine.structure.Move;

public class RandomExpansion extends ExpansionStrategy {

	private Random random;

	public RandomExpansion(GameDependentParameters gameDependentParameters, Random random){

		super(gameDependentParameters);

		this.random = random;
	}

	/*
	 * (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.expansion.ExpansionStrategy#expansionRequired(org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MCTSNode)
	 */
	@Override
	public boolean expansionRequired(MCTSNode node){
		if(node instanceof DecoupledMCTSNode){
			return this.decExpansionRequired((DecoupledMCTSNode)node);
		}else if(node instanceof SequentialMCTSNode){
			return this.seqExpansionRequired((SequentialMCTSNode)node);
		}/*else if(node instanceof SlowSeqentialMCTSNode){
			return this.sseqExpansionRequired((SlowSeqentialMCTSNode)node);
		}*/else{
			throw new RuntimeException("RandomExpansion-expansionRequired(): detected a node of a non-recognizable sub-type of class MCTSNode.");
		}
	}

	private boolean decExpansionRequired(DecoupledMCTSNode node) {

		int[] unexploredMovesCount = node.getUnexploredMovesCount();

		for(int i = 0; i < unexploredMovesCount.length; i++){
			if(unexploredMovesCount[i] > 0){
				return true;
			}
		}
		return false;
	}

	private boolean seqExpansionRequired(SequentialMCTSNode node){
		return node.getUnvisitedLeaves() != 0;
	}

	/*
	private boolean sseqExpansionRequired(SlowSeqentialMCTSNode node){
		return node.getUnvisitedLeaves().size() != 0;
	}
	*/

	/*
	 * (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.expansion.ExpansionStrategy#expand(org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MCTSNode)
	 */
	@Override
	public MCTSJointMove expand(MCTSNode node){
		if(node instanceof DecoupledMCTSNode){
			return this.decExpand((DecoupledMCTSNode)node);
		}else if(node instanceof SequentialMCTSNode){
			return this.seqExpand((SequentialMCTSNode)node);
		}/*else if(node instanceof SlowSeqentialMCTSNode){
			return this.sseqExpand((SlowSeqentialMCTSNode)node);
		}*/else{
			throw new RuntimeException("RandomExpansion-expand(): detected a node of a non-recognizable sub-type of class MCTSNode.");
		}
	}

	/**
	 * Random Expansion, DECOUPLED version.
	 * This method selects for each role a move that has not been visited yet and
	 * returns a joint move. If for a role all moves have been already visited, a
	 * random one is returned. Note that this will never happen if this method is
	 * called only after checking that the "expansionRequired" method returned true.
	 *
	 * @param node the node for which to choose a joint move.
	 * @return the joint move.
	 */
	private MCTSJointMove decExpand(DecoupledMCTSNode node){

		DecoupledMCTSMoveStats[][] moves = node.getMoves();
		int[] unexploredMovesCount = node.getUnexploredMovesCount();

		List<Move> jointMove = new ArrayList<Move>();
		int[] movesIndices = new int[moves.length];

		// For each role...
		for(int i = 0; i < moves.length; i++){
			// ...if all moves have been explored...
			if(unexploredMovesCount[i] == 0){
				// ...select a random one,...
				movesIndices[i] = this.random.nextInt(moves[i].length);
				jointMove.add(moves[i][movesIndices[i]].getTheMove());
			}else{ // ...otherwise, if at least one move is still unexplored...
				//...select a random one among the unexplored ones.
				int unexploredIndex = this.random.nextInt(unexploredMovesCount[i]);

				int moveIndex = -1;

				while(unexploredIndex > -1){
					moveIndex++;
					if(moves[i][moveIndex].getVisits() == 0){
						unexploredIndex--;
					}
				}

				movesIndices[i] = moveIndex;
				jointMove.add(moves[i][movesIndices[i]].getTheMove());

				// Do this when backpropagating and actually incrementing the number of visits
				// of this move from 0 to 1.
				//unexploredMovesCount[i]--;
			}
		}

		return new SequDecMCTSJointMove(jointMove, movesIndices);
	}


	/**
	 * Random Expansion, SEQUENTIAL version.
	 * This method selects a joint move that has not been explored yet.
	 * This is done by selecting a random unvisited leaf move in the tree of move statistics.
	 * A random number between 0 (included) and the number of unvisited moves (excluded) is picked.
	 * Then the joint move is reconstructed starting at the moves of myRole using the unvisitedSubleaves
	 * parameter of the moves statistics. The algorithm follows the path in the moves statistics tree that
	 * leads to the i-th leaf node that we want to visit.
	 *
	 * If all joint moves have been explored, this method returns a random one among them.
	 * Note that this will never happen if this method is called only after checking that
	 * the "expansionRequired" method returned true.
	 *
	 * @param node the node for which to choose a joint move.
	 * @return the joint move.
	 */
	private MCTSJointMove seqExpand(SequentialMCTSNode node){

		List<Move> jointMove = new ArrayList<Move>(this.gameDependentParameters.getNumRoles());
		int[] movesIndices = new int[this.gameDependentParameters.getNumRoles()];

		// Initialize ArrayList with numRoles null elements.
		for(int i = 0; i < this.gameDependentParameters.getNumRoles(); i++){
			jointMove.add(null);
		}

		if(node.getUnvisitedLeaves() == 0){
			// Get random joint move
			int i = 0;
			for(List<Move> legalMoves : node.getAllLegalMoves()){
				movesIndices[i] = this.random.nextInt(legalMoves.size());
				jointMove.add(legalMoves.get(movesIndices[i]));
			}
			return new SequDecMCTSJointMove(jointMove, movesIndices); // TODO rename!
		}

		// Get a random leaf move (the path that connects the leaf move to one of my role's moves will
		// give us the unexplored joint move to play).
		// Note that the value fo this parameter will be always relative to the node in the stats tree that
		// we are visiting (i.e. the 3rd descendant leaf node for the node of one of the moves of my player
		// might be the 1st descendant leaf node for the node of one of the moves of the next player).
		// e.g.
		// p1.moveA
		//		--> p2.moveA
		//			--> leafNode1
		//			--> leafNode2
		//		--> p2.moveB
		//			--> leafNode3 (3rd descendant leaf node for p1.moveA, but 1st descendant leaf node for p2.moveB)
		//			--> leafnode4
		int selLeafMove = this.random.nextInt(node.getUnvisitedLeaves());

		// Get the index of myRole.
		int roleIndex = this.gameDependentParameters.getMyRoleIndex();

		// Get the moves stats for myRole.
		SequentialMCTSMoveStats[] movesStats = node.getMovesStats();

		// For every role...
		while(movesStats != null){

			// Get the move that has the chosen random leaf as descendant
			for(int i = 0; i < movesStats.length; i++){
				if((selLeafMove - movesStats[i].getUnvisitedSubleaves()) < 0){ // Move found
					movesIndices[roleIndex] = i;
					jointMove.set(roleIndex, node.getAllLegalMoves().get(roleIndex).get(i));
					movesStats = movesStats[i].getNextRoleMovesStats();
					break;
				}else{
					selLeafMove -=  movesStats[i].getUnvisitedSubleaves();
				}
			}

			roleIndex = (roleIndex + 1)%this.gameDependentParameters.getNumRoles();
		}

		return new SequDecMCTSJointMove(jointMove, movesIndices);
	}

	/**
	 * Random Expansion, SLOW SEQUENTIAL version.
	 * This method selects a joint move that has not been explored yet.
	 * This is done by selecting a random unvisited leaf move in the tree of move statistics.
	 * Visiting the tree backward from the leaf allows to reconstruct the joint move.
	 * If all joint moves have been explored, this method returns a random one among them.
	 * Note that this will never happen if this method is called only after checking that
	 * the "expansionRequired" method returned true.
	 *
	 * @param node the node for which to choose a joint move.
	 * @return the joint move.
	 */
	/*
	private MCTSJointMove sseqExpand(SlowSeqentialMCTSNode node){

		List<SlowSequentialMCTSMoveStats> unvisitedLeaves = node.getUnvisitedLeaves();
		if(unvisitedLeaves.isEmpty()){
			return this.getRandomMove(node);
		}

		List<Move> jointMove = new ArrayList<Move>(this.numRoles);

		// Initialize ArrayList with numRoles null elements.
		for(int i = 0; i < this.numRoles; i++){
			jointMove.add(null);
		}

		SlowSequentialMCTSMoveStats leafMove = null;

		// Initialize the roleIndex to myRole index. In this way the first index for which a move will be
		// chosen will be the role index right before this one (numRoles-1 in case myRole index is 0).
		int roleIndex = this.myRole.getIndex();

		// Get a random leaf move (will be the selected move for myRole if it's a single-player game, or for
		// the role that comes right before myRole in the list of roles for a multi-player game).
		leafMove = unvisitedLeaves.get(this.random.nextInt(unvisitedLeaves.size()));
		SlowSequentialMCTSMoveStats chosenMove = leafMove;

		while(chosenMove != null){
			roleIndex = ((roleIndex-1) + this.numRoles)%this.numRoles;

			// Add the move to the joint move.
			jointMove.set(roleIndex, chosenMove.getTheMove());

			// Get the parent move, that will be the chosen move for the previous role.
			chosenMove = chosenMove.getPreviousRoleMoveStats();
		}

		return new SlowSequentialMCTSJointMove(jointMove, leafMove);
	}
	*/

	/**
	 * TODO: THIS METHOD MIGHT EVENTUALLY DISAPPEAR TOGETHER WITH THE WHOLE SLOW SEQUENTIAL GAMER, SO IT IS NOT
	 * ADAPTED TO BE SHARED WITH THE SEQUENTIAL VERSION OF MCTS.
	 * @param node
	 * @return
	 */
	/*
	private MCTSJointMove getRandomMove(SlowSeqentialMCTSNode node){

		List<Move> jointMove = new ArrayList<Move>(this.numRoles);

		// Initialize ArrayList with numRoles null elements.
		for(int i = 0; i < this.numRoles; i++){
			jointMove.add(null);
		}

		// Get the index of myRole.
		int roleIndex = this.myRole.getIndex();

		// Get a random move for myRole.
		SlowSequentialMCTSMoveStats[] movesStats = node.getMovesStats();
		SlowSequentialMCTSMoveStats chosenMove = null;

		while(movesStats != null){

			// Select a random move.
			chosenMove = movesStats[this.random.nextInt(movesStats.length)];

			// Add the move to the joint move.
			jointMove.set(roleIndex, chosenMove.getTheMove());

			// Get the next role moves.
			movesStats = chosenMove.getNextRoleMovesStats();

			// Compute the index for the next role
			roleIndex = (roleIndex+1)%this.numRoles;

		}

		return new SlowSequentialMCTSJointMove(jointMove, chosenMove);
	}
	*/

	@Override
	public String getStrategyParameters() {
		return null;
	}

	@Override
	public String printStrategy() {
		String params = this.getStrategyParameters();

		if(params != null){
			return "[EXPANSION_STRATEGY = " + this.getClass().getSimpleName() + ", " + params + "]";
		}else{
			return "[EXPANSION_STRATEGY = " + this.getClass().getSimpleName() + "]";
		}
	}

}

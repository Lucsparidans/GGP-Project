/**
 *
 */
package org.ggp.base.player.gamer.statemachine.MCTS.manager.propnet.treestructure;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import org.ggp.base.util.statemachine.inernalPropnetStructure.InternalPropnetMachineState;

/**
 * @author C.Sironi
 *
 */
public class MCTSTranspositionTable {

	private int currentGameStepStamp;

	private int gameStepOffset;

	/**
	 * The transposition table (implemented with HashMap that uses the internal propnet state as key
	 * and solves collisions with linked lists).
	 */
	private Map<InternalPropnetMachineState,MCTSNode> transpositionTable;

	/**
	 *
	 */
	public MCTSTranspositionTable(int gameStepOffset){
		this.currentGameStepStamp = 0;
		this.transpositionTable = new HashMap<InternalPropnetMachineState,MCTSNode>();
		this.gameStepOffset = gameStepOffset;
	}

	public MCTSNode getNode(InternalPropnetMachineState state){
		MCTSNode node = this.transpositionTable.get(state);
		if(node != null){
			//System.out.println("Found");
			node.setGameStepStamp(this.currentGameStepStamp);
		}/*else{
			System.out.println("Not found");
		}*/
		return node;
	}

	public void putNode(InternalPropnetMachineState state, MCTSNode node){
		if(node != null){
			this.transpositionTable.put(state, node);
			node.setGameStepStamp(this.currentGameStepStamp);
		}
	}

	public void clean(int newGameStepStamp){

		//System.out.println("Current TT game step: " + newGameStepStamp);
		//System.out.println("Cleaning TT with game step: " + newGameStepStamp);
		//System.out.println("Current TT size: " + this.transpositionTable.size());

		// Clean the table only if the game-step stamp changed (this is already checked by the caller).
		//if(newGameStepStamp != this.currentGameStepStamp){
			this.currentGameStepStamp = newGameStepStamp;
			// Remove all nodes last accessed earlier than the game step (newGameStepStamp-gameStepOffset)
			Iterator<Entry<InternalPropnetMachineState,MCTSNode>> iterator = this.transpositionTable.entrySet().iterator();
			while(iterator.hasNext()){
				Entry<InternalPropnetMachineState,MCTSNode> entry = iterator.next();
				if(entry.getValue().getGameStepStamp() < (this.currentGameStepStamp-this.gameStepOffset)){
					iterator.remove();
				}
			}

			//System.out.println("TT size after cleaning: " + this.transpositionTable.size());
		//}
	}

	public int getLastGameStep(){
		return this.currentGameStepStamp;
	}

}
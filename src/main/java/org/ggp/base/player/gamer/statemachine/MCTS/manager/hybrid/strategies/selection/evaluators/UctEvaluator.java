package org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.strategies.selection.evaluators;

import java.util.Random;

import org.ggp.base.player.gamer.statemachine.GamerSettings;
import org.ggp.base.player.gamer.statemachine.MCS.manager.MoveStats;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.GameDependentParameters;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.hybrid.SharedReferencesCollector;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.parameterstuning.structure.parameters.DoubleTunableParameter;
import org.ggp.base.player.gamer.statemachine.MCTS.manager.treestructure.MctsNode;
import org.ggp.base.util.statemachine.structure.Move;
/**
 * !!! FOR NOW THIS CLASS CANNOT TUNE FPU!
 * @author C.Sironi
 *
 */
public class UctEvaluator extends MoveEvaluator {

	/**
	 * This is an array so that it can memorize a different value for C for each role in the game.
	 * If a single value has to be used then all values in the array will be the same.
	 */
	protected DoubleTunableParameter c;

	/**
	 * Default value to assign to an unexplored move (first play urgency).
	 * This is an array so that it can memorize a different value for C for each role in the game.
	 * If a single value has to be used then all values in the array will be the same.
	 */
	protected DoubleTunableParameter fpu;

	public UctEvaluator(GameDependentParameters gameDependentParameters, Random random,
			GamerSettings gamerSettings, SharedReferencesCollector sharedReferencesCollector){

		super(gameDependentParameters, random, gamerSettings, sharedReferencesCollector);

		// Get default value for C (this is the value used for the roles for which we are not tuning the parameter)
		double fixedC = gamerSettings.getDoublePropertyValue("MoveEvaluator.fixedC");

		if(gamerSettings.getBooleanPropertyValue("MoveEvaluator.tuneC")){
			// If we have to tune the parameter then we look in the setting for all the values that we must use
			// Note: the format for these values in the file must be the following:
			// BetaComputer.valuesForK=v1;v2;...;vn
			// The values are listed separated by ; with no spaces
			this.c = new DoubleTunableParameter(fixedC, gamerSettings.getDoublePropertyMultiValue("MoveEvaluator.valuesForC"));

			// If the parameter must be tuned online, then we should add its reference to the sharedReferencesCollector
			sharedReferencesCollector.addParameterToTune(this.c);

		}else{
			this.c = new DoubleTunableParameter(fixedC);
		}

		// Get default value for Fpu (this is the value used for the roles for which we are not tuning the parameter)
		double fixedFpu = gamerSettings.getDoublePropertyValue("MoveEvaluator.fixedFpu");

		if(gamerSettings.getBooleanPropertyValue("MoveEvaluator.tuneFpu")){
			// If we have to tune the parameter then we look in the setting for all the values that we must use
			// Note: the format for these values in the file must be the following:
			// BetaComputer.valuesForK=v1;v2;...;vn
			// The values are listed separated by ; with no spaces
			this.fpu = new DoubleTunableParameter(fixedFpu, gamerSettings.getDoublePropertyMultiValue("MoveEvaluator.valuesForFpu"));

			// If the parameter must be tuned online, then we should add its reference to the sharedReferencesCollector
			sharedReferencesCollector.addParameterToTune(this.fpu);

		}else{
			this.fpu = new DoubleTunableParameter(fixedFpu);
		}

	}

	@Override
	public void setReferences(SharedReferencesCollector sharedReferencesCollector) {
		// No need for any reference
	}

	@Override
	public void clearComponent(){
		this.c.clearParameter();
		this.fpu.clearParameter();
	}

	@Override
	public void setUpComponent(){
		this.c.setUpParameter(this.gameDependentParameters.getNumRoles());
		this.fpu.setUpParameter(this.gameDependentParameters.getNumRoles());
	}

	@Override
	public double computeMoveValue(MctsNode theNode, Move theMove, int roleIndex, MoveStats theMoveStats) {

		double exploitation = this.computeExploitation(theNode, theMove, roleIndex, theMoveStats);
		double exploration = this.computeExploration(theNode, roleIndex, theMoveStats);

		if(exploitation != -1 && exploration != -1){
			return exploitation + exploration;
		}else{
			return this.fpu.getValuePerRole(roleIndex);
		}
	}

	protected double computeExploitation(MctsNode theNode, Move theMove, int roleIndex, MoveStats theMoveStats){

		double moveVisits = theMoveStats.getVisits();
		double score = theMoveStats.getScoreSum();

		if(moveVisits == 0){
			return -1.0;
		}else{
			return ((score / moveVisits) / 100.0);
		}

	}

	protected double computeExploration(MctsNode theNode, int roleIndex, MoveStats theMoveStats){

		int nodeVisits = theNode.getTotVisits();

		double moveVisits = theMoveStats.getVisits();

		if(nodeVisits != 0 && moveVisits != 0){

			return (this.c.getValuePerRole(roleIndex) * (Math.sqrt(Math.log(nodeVisits)/moveVisits)));
		}else{
			return -1.0;
		}

	}

	@Override
	public String getComponentParameters(String indentation) {

		return indentation + "C = " + this.c.getParameters(indentation + "  ") +
				indentation + "IFPU = " + this.fpu.getParameters(indentation + "  ");

	}

}

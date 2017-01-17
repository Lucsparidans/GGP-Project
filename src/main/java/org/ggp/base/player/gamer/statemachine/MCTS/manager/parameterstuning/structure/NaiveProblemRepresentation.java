package org.ggp.base.player.gamer.statemachine.MCTS.manager.parameterstuning.structure;

public class NaiveProblemRepresentation {

	/**
	 * The global Multi-Armed Bandit problem.
	 * Its arms correspond to the possible combinatorial moves seen so far.
	 */
	private IncrementalMab globalMab;

	/**
	 * For each parameter, a multi-armed bandit problem where each arm corresponds to a possible
	 * value that can be assigned to that parameter.
	 */
	private FixedMab[] localMabs;

	public NaiveProblemRepresentation(int[] classesLength) {

		this.globalMab = new IncrementalMab();

		this.localMabs = new FixedMab[classesLength.length];

		for(int i = 0; i < this.localMabs.length; i++){
			this.localMabs[i] = new FixedMab(classesLength[i]);
		}
	}

	public IncrementalMab getGlobalMab(){
		return this.globalMab;
	}

	public FixedMab[] getLocalMabs(){
		return this.localMabs;
	}

}

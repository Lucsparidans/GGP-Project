package ggpbasebenchmark;

import org.ggp.base.util.statemachine.MachineState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.exceptions.GoalDefinitionException;
import org.ggp.base.util.statemachine.exceptions.StateMachineException;

public abstract class SearchAlgorithm {

	protected long nbLegals;
	protected long nbUpdates;
	protected long nbGoals;
	private int playclock;
	protected StateMachine stateMachine;

	public SearchAlgorithm(int playclock) {
	}

	public SearchAlgorithm(StateMachine stateMachine, int playclock) {
		this.stateMachine = stateMachine;
		this.playclock = playclock;
	}

	public void run(final MachineState state) {
		reset();
		if (playclock == Integer.MAX_VALUE) {
			doSearch(state);
		} else {
			final Object notifier = new Object();
			Thread t = new Thread() {
				@Override
				public void run() {
					doSearch(state);
					synchronized (notifier) {
						notifier.notifyAll();
					}
				}
			};
			t.start();
			try {
				synchronized (notifier) {
					notifier.wait(playclock*1000); // wait until time is up or thread is finished
				}
				// time is up -> interrupt thread
				t.interrupt();
			} catch (InterruptedException e) {
				// thread finished and thus our notifier.wait(...) call got interrupted
			}
		}
	}

	public abstract void doSearch(MachineState state);

	public void reset() {
		nbLegals = 0;
		nbUpdates = 0;
		nbGoals = 0;
	}

	public long getNbLegals() {
		return nbLegals;
	}

	public long getNbUpdates() {
		return nbUpdates;
	}

	public long getNbGoals() {
		return nbGoals;
	}

	public int getPlayclock() {
		return playclock;
	}

	public void setPlayclock(int playclock) {
		this.playclock = playclock;
	}

	public void evaluateGoals(MachineState state) throws StateMachineException {
		try {
			stateMachine.getGoals(state);
			++nbGoals;
		} catch (GoalDefinitionException e) {
			e.printStackTrace();
		}
	}

	public boolean timeout() {
		return Thread.currentThread().isInterrupted();
	}

}
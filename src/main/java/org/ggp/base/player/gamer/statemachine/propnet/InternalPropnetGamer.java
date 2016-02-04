/**
 *
 */
package org.ggp.base.player.gamer.statemachine.propnet;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.ggp.base.player.gamer.exception.GamePreviewException;
import org.ggp.base.player.gamer.statemachine.StateMachineGamer;
import org.ggp.base.util.game.Game;
import org.ggp.base.util.gdl.grammar.GdlPool;
import org.ggp.base.util.propnet.architecture.separateExtendedState.immutable.ImmutablePropNet;
import org.ggp.base.util.propnet.creationManager.SeparateInternalPropnetCreationManager;
import org.ggp.base.util.propnet.state.ImmutableSeparatePropnetState;
import org.ggp.base.util.statemachine.StateMachine;
import org.ggp.base.util.statemachine.cache.CachedStateMachine;
import org.ggp.base.util.statemachine.implementation.internalPropnet.InternalPropnetStateMachine;
import org.ggp.base.util.statemachine.implementation.internalPropnet.SeparateInternalPropnetStateMachine;
import org.ggp.base.util.statemachine.implementation.prover.ProverStateMachine;

/**
 * This gamer tries to use the state machine based on the internal propnet.
 * If the propnet fails to build, the gamer will use the cached prover.
 *
 * The gamer can be set in two ways:
 * - [singleGame = true]: the player assumes to be playing always the same game
 *   and thus tries to build the propnet state machine only the first time. If
 *   it succeeds it will always use the same propnet state machine for every match,
 *   otherwise it will always return the prover state machine.
 *   ATTENTION! DANGER! If you use this setting make sure that the Game Manager always sends
 *   exactly the same game description or the player will start behaving in a weird way. The
 *   game description, for example, must not be scrambled or the names of the moves will be
 *   different and the gamer will return wrong moves.
 * - [singleGame = false] (default): the player will try for each match to build the propnet
 *   state machine of the given game and return the prover state machine every time
 *   it fails building the propnet.
 *
 * All the gamers that want to use the internal propnet must extend this class.
 *
 * @author C.Sironi
 *
 */
public abstract class InternalPropnetGamer extends StateMachineGamer {

	/**
	 * Used to tell to the player if and how to build the propnet state machine.
	 *
	 * More precisely:
	 * - ALWAYS = the player will build a new propnet and thus a new state machine
	 * 			  every time a new match starts (i.e. everytime the getInitialStateMachine
	 * 			  is called).
	 * - ONCE = the player will assume to always be playing the same game and thus
	 * 			it will use the same propnet state machine and build it only the
	 * 			first time.
	 * - NEVER = the player will never build a new propnet and thus never a new state
	 * 			 machine, but will use the one (if any) that is given as input to the
	 * 			 constructor.
	 *
	 * @author C.Sironi
	 *
	 */
    public enum PROPNET_BUILD{
    	ALWAYS, ONCE, NEVER
    }

	/**
	 * The player must complete the executions of methods with a timeout by the time
	 * [timeout - safetyMargin(ms)] to increase the certainty of answering to the Game
	 * Manager in time.
	 */
	protected long safetyMargin;

	/**
	 * The personal reference to the propnet machine.
	 */
	protected InternalPropnetStateMachine thePropnetMachine;

	/**
	 *
	 */
	protected PROPNET_BUILD propnetBuild;

	/**
	 * True if this gamer never tried to build a propnet before.
	 * It is used when the gamer is assumed to play always the same game and returns the
	 * initial state machine. If this gamer has no state machine it might be because it is
	 * the first time the player is being used
	 * or because it already tried to build a state machine based on the propnet and failed,
	 * so we should avoid trying again.
	 */
	private boolean firstTry;

	/**
	 *
	 */
	public InternalPropnetGamer() {
		// TODO: change code so that the parameters can be set from outside.
		this.safetyMargin = 10000L;
		this.thePropnetMachine = null;
		this.propnetBuild = PROPNET_BUILD.ALWAYS;
		this.firstTry = true;

	}

	/**
	 *
	 */
	public InternalPropnetGamer(InternalPropnetStateMachine thePropnetMachine){
		// TODO: change code so that the parameters can be set from outside.
		this.safetyMargin = 10000L;
		this.thePropnetMachine = thePropnetMachine;
		this.propnetBuild = PROPNET_BUILD.NEVER;
		this.firstTry = true;

	}

	/* (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.StateMachineGamer#getInitialStateMachine()
	 */
	@Override
	public StateMachine getInitialStateMachine() {

		LOGGER.info("[Gamer] Returning initial state machine.");

		switch(this.propnetBuild){
		case ALWAYS: // Create a new state machine for every game:
			LOGGER.info("[Gamer] Standard gamer (not single-game).");
			LOGGER.info("[Gamer] Creating state machine for the game.");

			return this.createStateMachine();
		case ONCE: // Build once, then re-use:

			LOGGER.info("[Gamer] Single-game gamer.");

			// If the propnet machine already exists, return it.
			if(this.thePropnetMachine != null){

				LOGGER.info("[Gamer] Propnet state machine already created for the game. Returning same state machine.");
				//System.out.println("Returning SAME propnet state machine.");

				return this.thePropnetMachine;
			}

			// Otherwise, if it doesn't exist because we never tried to build a propnet,
			// create it and return it.
			if(this.firstTry){

				this.firstTry = false;

				LOGGER.info("[Gamer] First try to create the propnet state machine.");

				return this.createStateMachine();

			}else{

				LOGGER.info("[Gamer] Already tried to build propnet and failed. Returning prover state machine.");
				//System.out.println("Already FAILED with propnet, not gonna try again: returning prover state machine.");

				return new CachedStateMachine(new ProverStateMachine());
			}
		case NEVER:
			return this.thePropnetMachine;
		}

		return null;

	}

	private StateMachine createStateMachine(){
		if(System.currentTimeMillis() < this.getMetagamingTimeout() - this.safetyMargin){

	        // Create the executor service that will run the propnet manager that creates the propnet
	        ExecutorService executor = Executors.newSingleThreadExecutor();

	        // Create the propnet creation manager
	        SeparateInternalPropnetCreationManager manager = new SeparateInternalPropnetCreationManager(getMatch().getGame().getRules(), this.getMetagamingTimeout());

	        // Start the manager
	  	  	executor.execute(manager);

	  	  	// Shutdown executor to tell it not to accept any more task to execute.
			// Note that this doesn't interrupt previously started tasks.
			executor.shutdown();

			// Tell the executor to wait until the currently running task has completed execution or the timeout has elapsed.
			try{
				executor.awaitTermination(this.getMetagamingTimeout() - System.currentTimeMillis() - this.safetyMargin, TimeUnit.MILLISECONDS);
			}catch(InterruptedException e){ // The thread running the gamer has been interrupted => stop playing.
				executor.shutdownNow(); // Interrupt everything
				LOGGER.error("[Gamer] Gamer interrupted while computing initial propnet state machine: returning prover state machine.", e);
				Thread.currentThread().interrupt();

				//System.out.println("Returning prover state machine.");

				return new CachedStateMachine(new ProverStateMachine());
			}

			// Here the available time has elapsed, so we must interrupt the thread if it is still running.
			executor.shutdownNow();

			// If the thread is terminated, we can get the propnet, otherwise we return the prover.
			// TODO: if the thread isn't terminated, we don't wait for it to do so, we ignore the propnet
			// and give back the prover. A check must be added: it could be that the manager built the
			// propnet but it is still busy optimizing it. In this case the last completed optimization
			// of the propnet is usable so we should not discard it! For example, if the thread isn't
			// terminated, we could wait for half of the time still available and check again. We cannot
			// get the propnet before being sure that the manager has terminated, otherwise we risk getting
			// one in an inconsistent state. Also the manager must be fixed so that if it gets interrupted
			// while running an optimization it can return the propnet and its state at the previous optimization.
			if(executor.isTerminated()){

				// If we are here it means that the manager stopped running. We must check if it has created a usable propnet or not.
				ImmutablePropNet propnet = manager.getImmutablePropnet();
				ImmutableSeparatePropnetState propnetState = manager.getInitialPropnetState();

				if(propnet != null && propnetState != null){

					// Create the state machine giving it the propnet and the propnet state.
				    this.thePropnetMachine =  new SeparateInternalPropnetStateMachine(propnet, propnetState);

				    LOGGER.info("[Gamer] Propnet built successfully: returning propnet state machine.");
				    //System.out.println("Returning propnet state machine.");

				    return this.thePropnetMachine;
				}else{
					LOGGER.info("[Gamer] Propnet builder ended execution but at least one among the immutable propnet structure and the propnet state is null: returning prover state machine.");
				}
			}else{
				LOGGER.info("[Gamer] The propnet state machine didn't build in time: returning prover state machine.");
			}
		}else{
			LOGGER.info("[Gamer] No time to build propnet state machine: returning prover state machine.");
		}

		//System.out.println("Returning prover state machine.");

		return new CachedStateMachine(new ProverStateMachine());
	}



	/* (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.StateMachineGamer#stateMachineStop()
	 */
	@Override
	public void stateMachineStop() {
		System.gc();
		if(this.propnetBuild == PROPNET_BUILD.ALWAYS){
			GdlPool.drainPool();
		}
	}

	/* (non-Javadoc)
	 * @see org.ggp.base.player.gamer.statemachine.StateMachineGamer#stateMachineAbort()
	 */
	@Override
	public void stateMachineAbort() {
		System.gc();
		if(this.propnetBuild == PROPNET_BUILD.ALWAYS){
			GdlPool.drainPool();
		}
	}

	/* (non-Javadoc)
	 * @see org.ggp.base.player.gamer.Gamer#preview(org.ggp.base.util.game.Game, long)
	 */
	@Override
	public void preview(Game g, long timeout) throws GamePreviewException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.ggp.base.player.gamer.Gamer#getName()
	 */
	@Override
	public String getName() {
		/*String type = "";
		if(this.singleGame){
			type = "SingleGame";
		}else{
			type = "Starndard";
		}
		return getClass().getSimpleName() + "-" + type;*/
		return getClass().getSimpleName();
	}
}

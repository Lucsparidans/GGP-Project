package org.ggp.base.player.request.grammar;

import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.StructuredDataMessage;
import org.ggp.base.player.event.PlayerTimeEvent;
import org.ggp.base.player.gamer.Gamer;
import org.ggp.base.player.gamer.event.GamerUnrecognizedMatchEvent;
import org.ggp.base.player.gamer.exception.MoveSelectionException;
import org.ggp.base.util.gdl.grammar.GdlTerm;


public final class PlayRequest extends Request
{
	/**
	 * Static reference to the logger
	 */
	private static final Logger LOGGER;

	static{
		LOGGER = LogManager.getRootLogger();
	}

	private final Gamer gamer;
	private final String matchId;
	private final List<GdlTerm> moves;

	public PlayRequest(Gamer gamer, String matchId, List<GdlTerm> moves)
	{
		this.gamer = gamer;
		this.matchId = matchId;
		this.moves = moves;
	}

	@Override
	public String getMatchId() {
		return matchId;
	}

	@Override
	public String process(long receptionTime)
	{
	    // First, check to ensure that this play request is for the match
	    // we're currently playing. If we're not playing a match, or we're
	    // playing a different match, send back "busy".
		if (gamer.getMatch() == null || !gamer.getMatch().getMatchId().equals(matchId)) {
			gamer.notifyObservers(new GamerUnrecognizedMatchEvent(matchId));
			LOGGER.warn(new StructuredDataMessage("" + System.currentTimeMillis(), "Got PLAY message not intended for current game: ignoring.", "GamePlayer"));
			return "busy";
		}

		if (moves != null) {
			gamer.getMatch().appendMoves(moves);
		}

		try {
			gamer.notifyObservers(new PlayerTimeEvent(gamer.getMatch().getPlayClock() * 1000));
			return gamer.selectMove(gamer.getMatch().getPlayClock() * 1000 + receptionTime).toString();
		} catch (MoveSelectionException e) {
			LOGGER.error(new StructuredDataMessage("" + System.currentTimeMillis(), e.getMessage(),"GamePlayer"));
			return "nil";
		}
	}

	@Override
	public String toString()
	{
		return "play";
	}
}
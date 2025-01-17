/* 
** Author(s): Miguel Calejo
** Contact:   interprolog@declarativa.com, http://www.declarativa.com, http://www.xsb.com
** Copyright (C) XSB Inc., USA, 2001-2005
** Use and distribution, without any warranties, under the terms of the 
** GNU Library General Public License, readable in http://www.fsf.org/copyleft/lgpl.html
*/
package com.xsb.interprolog;
import com.declarativa.interprolog.*;
import com.declarativa.interprolog.util.*;
import com.declarativa.interprolog.gui.*;
import javax.swing.*;

/** A ListenerWindow for a NativeEngine */
public class NativeEngineWindow extends ListenerWindow {
	
	public NativeEngineWindow(NativeEngine e){
		this(e,true);
		setTitle("NativeEngine listener ("+e.getPrologVersion()+")");
	}
	public NativeEngineWindow(NativeEngine e,boolean autoDisplay){
		super(e,autoDisplay);
		prologInput.setToolTipText("Prolog goal, sent when you press enter. Drag and drop .P files here to consult them");
        prologInput.getAccessibleContext().setAccessibleDescription(prologInput.getToolTipText());
		prologOutput.setToolTipText("Goals and their first solutions");
        prologOutput.getAccessibleContext().setAccessibleDescription(prologOutput.getToolTipText());
	}
	public void sendToProlog(){
		String goal = prologInput.getText().trim();
		if (goal.equals(";")) {
			beep();
			prologOutput.append("Sorry, no multiple solutions available with NativeEngine; use SubprocessEngine if you need them\n");
			return;
		}

                // if user puts '.' in the end of goal remove it
		if (goal.endsWith(".")) {
                    goal = goal.substring(0, goal.length() - 1);
		}

		if (goal.length()==0) {
			beep();
			prologOutput.append("Goal must be nonempty\n");
			return;
		}

                final String finalGoal = goal;
                prologOutput.append(finalGoal+"\n");
		addToHistory();
		// Better not call Prolog in the event thread, it would break modal dialogs
		Thread t = new Thread(){
			String result;
			public void run(){
				try{
					Object[] bindings = engine.deterministicGoal(finalGoal,null);
					result = formatGoalResult(bindings);
					
				} catch (IPInterruptedException e){
					result = "Goal was interrupted!";
				} catch (IPAbortedException e){
					result = "Goal was aborted!";
				} catch (IPException e){
					result = "Goal was aborted! \n"+e.getMessage();
				}
				SwingUtilities.invokeLater(new Runnable() {
					public void run(){
					prologOutput.append(result+"\n");
						scrollToBottom();
						focusInput();
					}
				});
			}
		};
		t.setName("NativeEngineWindow command");
		t.start();
	}
	
        protected String formatGoalResult(Object[] bindings){
            if (bindings == null) {return("FAILED\n");}
            else return(bindings[0].toString()+"\n");
        }

	/** Useful for launching the system, by passing the full Prolog directory path and 
	optionally extra arguments, that are passed to the Prolog command */
	public static void main(String[] args){
		commonMain(args);
		if(prologStartCommand.indexOf(' ')!=-1){
			System.err.println("Beware that any extra arguments in your command line will NOT");
			System.err.println("be considered by Prolog. For finer control use the NativeEngine class directly");
		}
		// new NativeEngineWindow(new NativeEngine(PrologEngine.prologBinToBaseDirectory(prologStartCommand),debug));
		new NativeEngineWindow(new NativeEngine(prologStartCommand,debug,loadFromJar));
	}

}
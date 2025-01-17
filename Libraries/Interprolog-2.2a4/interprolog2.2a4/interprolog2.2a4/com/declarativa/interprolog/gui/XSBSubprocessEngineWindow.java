/* 
** Author(s): Miguel Calejo
** Contact:   interprolog@declarativa.com, http://www.declarativa.com
** Copyright (C) Declarativa, Portugal, 2000-2004
** Use and distribution, without any warranties, under the terms of the 
** GNU Library General Public License, readable in http://www.fsf.org/copyleft/lgpl.html
*/
package com.declarativa.interprolog.gui;
import com.declarativa.interprolog.*;

public class XSBSubprocessEngineWindow extends SubprocessEngineWindow{
	public XSBSubprocessEngineWindow(XSBSubprocessEngine e){
		super(e);
	}
	public XSBSubprocessEngineWindow(XSBSubprocessEngine e,boolean autoDisplay){
		super(e,autoDisplay);
	}	
	/** Useful for launching the system, by passing the full Prolog executable path and 
	optionally extra arguments, that are passed to the Prolog command */
	public static void main(String[] args){
		commonMain(args);
		new XSBSubprocessEngineWindow(new XSBSubprocessEngine(prologStartCommand,debug,loadFromJar));
	}
}
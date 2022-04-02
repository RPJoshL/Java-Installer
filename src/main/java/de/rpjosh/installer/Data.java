package de.rpjosh.installer;

import java.io.PrintWriter;
import java.io.StringWriter;

class Data {

	private InstallConfig conf;
	
	Data(InstallConfig conf) {
		this.conf = conf;
	}
	
	/**
	 * Prints an error message to the console
	 * 
	 * @param v			the type of the information (d = debug, i = information, w = warning, e = error)
	 * @param anz 		the corresponding message
	 * @param location 	in which method / code part the error appeared
	 */
	public void log(String v, String anz, String location) {
		
		String anzSimple = anz.split("\n")[0].trim();
		
		String commandLine = "";
		// if the debug mode isn't enabled print only the first line of the message (without the location)
		if (true) commandLine = anz + " - " + location;
		else commandLine = anzSimple;
		
		
		if (v.equals("d")) {
			//logger.debug(anz); loggerSimple.debug(anzSimple);
			System.out.println("[D] " + commandLine);
		}
		
		if (v.equals("i")) {
			//logger.info(anz); loggerSimple.info(anzSimple);
			System.out.println("[I] " + commandLine);
		}
		
		if (v.equals("w")) {
			//logger.warn(anz); loggerSimple.warn(anzSimple);
			System.out.println("[W] " + commandLine);
		}
		
		if (v.equals("e")) {
			//logger.error(anz); loggerSimple.error(anzSimple);
			System.out.println("[E] " + commandLine);
		}
	}
	
	
	/**
	 * Gives the stack trace the logger
	 * @param v			the error code
	 * @param ex 		the error
	 * @param location  in which method / code part the error appeared
	 */
	public void log(String v, Exception ex, String location) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		
		ex.printStackTrace(pw);
		log("e", sw.toString(), location);
	}
}

package de.rpjosh.installer;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class RunInConsole {


	/**
	 * Opens the program inside a console if not already run inside a console window
	 * 
	 * @param keepOpen	the console will stay opened after the program was closed
	 */
	public static void start(boolean keepOpen) { 
		start (keepOpen, null, false, false); 
	}


	/**
	 * Opens the program inside a console if not already run inside a console window
	 * 
	 * @param args				the command line options for the main method when calling the program inside the console again
	 * @param keepOpen			the console will stay opened after the program finishes
	 */
    public static void start(String[] args, boolean keepOpen) {
        start(keepOpen, args, false, false);
    }
    
    /**
	 * Opens the program inside a console if not already run inside a console window
	 * 
	 * @param args				the command line options for the main method when calling the program inside the console again
	 * @param keepOpen			the console will stay opened after the program finishes
     * @param forceRestart		even restart the program when already running inside a console
     * @param asAdmin			start the console with administrator privileges (on Windows Powershell is required)
     */
    public static void start(String[] args, boolean keepOpen, boolean forceRestart, boolean asAdmin) {
        start(keepOpen, args, forceRestart, asAdmin);
    }


	private static void start(boolean keepOpen, final String[] args, boolean forceRestart, boolean asAdmin) {

    	String executableName = getExecutableName();
       
		// probably executed inside IDE
    	if (executableName == null) return;
    	// is already executed in console
    	if (System.console() != null && !forceRestart) return;

    	startExecutableInConsole(executableName, keepOpen, asAdmin, args);

    	System.exit(0);
	}


	/**
	 * Opens the console windows and starts the jar file
	 *
	 * @param executableName		the name of the jar file (without the path -> relativ)
	 * @param stayOpenAfterEnd		keep the console windows opened after the run of the jar file
	 * @param asAdmin				start the console with administrator privileges (on Windows Powershell is required)
	 */
	private static void startExecutableInConsole(String executableName, final boolean keepOpen, final boolean asAdmin, String[] args) {
		
		String command = null;
		
		// determine the parameters
		String strArgs = "";
		for (String currentArg: args) {
			strArgs += "\"" + currentArg + "\" ";
		}

		switch (InstallConfig.getOsType()) {
		case UNDETERMINED: break;
		case WINDOWS:
			if (!asAdmin) {
        		if (keepOpen) command = "cmd /c start cmd /k java -jar \"" + executableName + "\" " + strArgs;
        		else command = "cmd /c start java -jar \"" + executableName +"\" " + strArgs;
        	} else {
        		// da die Administratorkonsole im C:/Windows/System32 pfad geöffnet wird, muss der absolute Pfad der Jar-Datei ermittelt werden
        		executableName = new File(executableName).getAbsolutePath();
        		
        		if (keepOpen) command = "powershell \"Start-Process cmd -Verb RunAs -ArgumentList '/C', 'start cmd /k java -jar \" " + executableName + "\" " + strArgs + "'";
        		else command = "powershell \"Start-Process cmd -Verb RunAs -ArgumentList '/C', 'java -jar \"" + executableName + "\" " + strArgs + "'";
        	}
        	break;
        case LINUX: 
        	
    		executableName = new File(executableName).getAbsolutePath();
        	String terminal = null;
        	String terminalCommand = null;
        	
        	// es muss zunächst ein installiertes Terminal "gefunden" werden, das geöffnet werden kann
        	try {
            	String[][] terminals = { { "gnome-terminal", "--"}, {"xterm", "-e"}, {"xfce4-terminal", "-e"}, {"tilix", "-e"}, {"konsole", "-e"}, {"terminal", "-e"}};
            	
            	for (String currentTerminal[]: terminals) {
            		Process p = new ProcessBuilder("bash", "-c", "which " + currentTerminal[0]).start();
    				p.waitFor(5000, TimeUnit.SECONDS);
    				String output = "";
    				BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
    				output = buf.readLine();
    				
    				if (output != null && !output.equals("")) { terminal = currentTerminal[0]; terminalCommand = currentTerminal[1]; break; }
            	}
            	
            	if (terminal == null) break;
            		
            	if (!asAdmin) {
            		if (keepOpen) new ProcessBuilder("bash", "-c", terminal + " " + terminalCommand + " /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "; exec bash'").start();
            		else          new ProcessBuilder("bash", "-c", terminal + " " + terminalCommand + " /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "'").start();
            	} else {
            		if (keepOpen) new ProcessBuilder("bash", "-c", terminal + " " + terminalCommand + " sudo /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "; exec bash'").start();
            		else          new ProcessBuilder("bash", "-c", terminal + " " + terminalCommand + " sudo /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "'").start();
            	}
            	break;
        	} catch (Exception ex) { }        	
        	break;
        case MACOS: break;
        }

		try {
			if (command != null) Runtime.getRuntime().exec(command);
		} catch (IOException ex) {
			ex.printStackTrace();
		}
	}


	/**
	 * @return the name of the jar file <i> (MyInstaller.jar) </i>
	 */
	public static String getExecutableName() {

		String executableNameFromClass = null;
        
		final CodeSource codeSource = RunInConsole.class.getProtectionDomain().getCodeSource();
		if (codeSource == null) {
			// do nothing
		} else {
			String path = codeSource.getLocation().getPath();
			if (path == null || path.isEmpty()) {
				// do nothing
			} else {
				executableNameFromClass = new File(path).getName();
			}
		}

        String nameFromJavaClassPath = System.getProperty("java.class.path");
        String nameFromSunProperty = System.getProperty("sun.java.command");

        if (isJarFile(executableNameFromClass))  return executableNameFromClass;

        if (isJarFile(nameFromJavaClassPath)) return nameFromJavaClassPath;

        if (isJarFile(nameFromSunProperty)) return nameFromSunProperty;

        return null;
    }


	/**
	 * Checks if the given file path is valid and if its a jar file
	 * 
	 * @param	the name of the jar file
	 * @return 	the file path is valid and a jar file
	 */
	private static boolean isJarFile(final String name) {

		if (name == null || !name.toLowerCase().endsWith(".jar")) return false;
        
		// checks if file exists
		final File file = new File(name);
		return file.exists() && file.isFile();
	}

}

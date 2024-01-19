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
       
		// Probably executed inside an IDE
    	if (executableName == null) return;
    	// Application is already executed within a console
    	if (System.console() != null && !forceRestart) return;

    	startExecutableInConsole(executableName, keepOpen, asAdmin, args);

    	System.exit(0);
	}


	/**
	 * Opens a console window and starts the provided jar file.
	 * The executable name is NOT escaped because it shouldn't be critical. Make sure to provide a valid name.
	 *
	 * @param executableName		the name of the jar file (without the path -> relative)
	 * @param stayOpenAfterEnd		keep the console windows opened after the run of the jar file
	 * @param asAdmin				start the console with administrator privileges (for Windows a Poowershell is required)
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
        		// Because the administrative terminal is opened in 'C:/Windows/System32',
				// we need to query the absolute path of the JAR file before starting it
        		executableName = new File(executableName).getAbsolutePath();
        		
        		if (keepOpen) command = "powershell \"Start-Process cmd -Verb RunAs -ArgumentList '/C', 'start cmd /k java -jar \" " + executableName + "\" " + strArgs + "'";
        		else command = "powershell \"Start-Process cmd -Verb RunAs -ArgumentList '/C', 'java -jar \"" + executableName + "\" " + strArgs + "'";
        	}
        	break;
        case LINUX: 
        	
    		executableName = new File(executableName).getAbsolutePath();
        	String terminal = null;
        	String terminalCommand = null;
        	
        	// Find a installed terminal that we can use to opened up a new terminal
        	try {
            	String[][] terminals = { 
            			{ "gnome-terminal", "--"}, {"xterm", "-e"}, {"xfce4-terminal", "-e"}, {"tilix", "-e"}, {"konsole", "-e"}, {"terminal", "-e"},
            			{ "wezterm", "start -e" }, { "alacritty", "-e" }
            	};
            	
            	// Find the first available terminal
            	for (String currentTerminal[]: terminals) {
    				if (isCommandAvailable(currentTerminal[0])) { 
    					terminal = currentTerminal[0]; terminalCommand = currentTerminal[1]; break; 
    				}
            	}

            	if (terminal == null) break;
            		
            	if (!asAdmin) {
            		if (keepOpen) new ProcessBuilder("sh", "-c", terminal + " " + terminalCommand + " /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "; exec sh'").start();
            		else          new ProcessBuilder("sh", "-c", terminal + " " + terminalCommand + " /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "'").start();
            	} else {
            		
            		if (isCommandAvailable("pkexec")) {
            			// A polkit daemon is available on the system. We use it to authenticate the installer as root
            			Process proc = new ProcessBuilder(
            					"pkexec", "--user", "root", 
                    			// By default, the command started by pkexec will run in a minimal and safe environment. This does NOT include the $DISPLAY variable by default.
                    			// Because the most terminal needs this (and  the XAUTH), we use the env command
            					"env", "DISPLAY=" + System.getenv("DISPLAY"), "XAUTH=" + System.getenv("XAUTH"), "HOME=" + System.getenv("HOME"),
            					"/bin/sh", "-c", 
            					terminal + " " + terminalCommand + " /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs 
            				+   (keepOpen ? "; exec sh'" : ";")
            			).inheritIO().start();
            			
            			// The polkit process runs in foreground. So we need to wait until the installation process finished
            			synchronized(proc) {
            				proc.wait();
            			}
            		} else {
                		if (keepOpen) new ProcessBuilder("sh", "-c", terminal + " " + terminalCommand + " sudo /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "; exec sh'").start();
                		else          new ProcessBuilder("sh", "-c", terminal + " " + terminalCommand + " sudo /bin/sh -c 'java -jar \"" + executableName + "\" " + strArgs + "'").start();
            		}

            	}
            	break;
        	} catch (Exception ex) { ex.printStackTrace(); }        	
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
	 * Queries if the provided command or application is available on this system.
	 * 
	 * @param command	Command to check
	 * 
	 * @return			Weather the command is available or not
	 * 
	 * @throws IOException 
	 * @throws InterruptedException 
	 */
	private static boolean isCommandAvailable(String command) throws IOException, InterruptedException {
		Process p = new ProcessBuilder("sh", "-c", "which " + command).start();
		p.waitFor(2000, TimeUnit.SECONDS);
		String output = "";
		BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
		output = buf.readLine();
		
		return output != null && !output.isBlank();
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

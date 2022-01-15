package tk.rpjosh.installer;

import java.io.File;
import java.io.IOException;
import java.security.CodeSource;
import java.util.concurrent.TimeUnit;
import java.io.BufferedReader;
import java.io.InputStreamReader;


public class RunInConsole {


	/**
	 * Öffnet das Programm in der Konsole, falls dieses noch nicht in solch einer ausgeführt wird
	 * @param keepOpen	 Ob die Konsole nach dem Durchlauf des Programms geschlossen werden soll
	 */
	public static void start(boolean keepOpen) { 
		start (keepOpen, null, false, false); 
	}


	/**
	 * Öffnet das Programm in der Konsole, falls dieses noch nicht in solch einer ausgeführt wird
	 * @param args				Mit welchen Parametern die Main-Methode beliefert werden soll
	 * @param keepOpen			Ob die Konsole nach dem durchlauf des Programms geöffnet bleiben soll
	 */
    public static void start(String[] args, boolean keepOpen) {
        start(keepOpen, args, false, false);
    }
    
    /**
     * Öffnet das Programm in der Konsole
	 * @param args				Mit welchen Parametern die Main-Methode beliefert werden soll
     * @param keepOpen			Ob die Konsole nach dem durchlauf des Programms geöffnet bleiben soll
     * @param forceRestart		Ob ein Neustart gemacht werden soll, wenn das Programm bereits in der Konsole läuft
     * @param asAdmin			Ob das Programm mit Administratorprivelegien gestartet werden soll (Powershell muss installiert sein)
     */
    public static void start(String[] args, boolean keepOpen, boolean forceRestart, boolean asAdmin) {
        start(keepOpen, args, forceRestart, asAdmin);
    }


	private static void start(boolean keepOpen, final String[] args, boolean forceRestart, boolean asAdmin) {

    	String executableName = getExecutableName();
       
		// wird vermutlich in einer IDE ausgeführt
    	if (executableName == null) return;
    	// wird bereits in der Konsole ausgeführt
    	if (System.console() != null && !forceRestart) return;

    	startExecutableInConsole(executableName, keepOpen, asAdmin, args);

    	System.exit(0);
	}


	/**
	 * Öffnen ein Konsolenfenster und startet in diesem die Jar-Datei
	 *
	 * @param executableName		Der Name der Jar-Datei (ohne Pfad -> relativ)
	 * @param stayOpenAfterEnd		Ob das Konsolenfenster nach dem vollständigen durchlauf der Jar-Datei geschlossen werden soll
	 * @param asAdmin				Ob das Programm mit Administratorprivelegien gestartet werden soll (Powershell muss installiert sein)
	 */
	private static void startExecutableInConsole(String executableName, final boolean keepOpen, final boolean asAdmin, String[] args) {
		
		String command = null;
		
		// es müssen nun noch die Parameter ermittelt werden
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
	 * @return Der Name der Jar-Datei <i> (BeMa_installer.jar) </i>
	 */
	public static String getExecutableName() {

		String executableNameFromClass = null;
        
		final CodeSource codeSource = RunInConsole.class.getProtectionDomain().getCodeSource();
		if (codeSource == null) {
			// es wird nichts geloggt
		} else {
			String path = codeSource.getLocation().getPath();
			if (path == null || path.isEmpty()) {
				// es wird nichts geloggt;
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
	 * Gibt zurück, ob es sich um eine .jar Datei handelt, und ob diese existiert
	 * @param name Name der Jar Datei
	 * @return Ob es sich um eine Jar-Datei handelt
	 */
	private static boolean isJarFile(final String name) {

		if (name == null || !name.toLowerCase().endsWith(".jar")) return false;
        
		// überprüfe, ob diese existiert
		final File file = new File(name);
		return file.exists() && file.isFile();
	}


}

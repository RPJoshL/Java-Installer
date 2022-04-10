package de.rpjosh.installer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.File;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;

public class InstallConfig {

	private String company;
	private String applicationNameShort;
	private String applicationNameLong;
	private String version;
	private int estimatedSize = 0;
	
	ArrayList<String> directorysInConfig = new ArrayList<String>();
	ArrayList<String> directorysInAppData = new ArrayList<String>();
	Map<String, String> fontsToInstall = new HashMap<String, String>();
	
	// All paths used by the program //
	private String desktopDir = null;
	private String applicationDir = null;
	private String configDir = null;
	private String jarRuntimeLocation = "";
	
	private boolean quiet = false;
	private boolean debug = false;
	private boolean isPortable = false;
	private boolean isUser = false;
	private boolean offline = false;
	private String portableMainDir = "";
	
	private Logger logger;
	
	/* File structure when portable:
	.
	+-- _Programm
	|   +-- _pics
	|   +-- MyProgram.jar
	|	+-- portable		<- Through this file portable will be annotated
	+-- _AppData
	|   +-- _config
	|	|	+-- conf.txt
	|	+-- _logs
	|	|	+-- MyProgram.log
	|	|	+-- MyProgram_simple.log
	+-- ShortcutToJar
	
	*/
	
	// Settings for program download //
	protected String downloadURL = null;
	protected boolean addVersion = false;
	protected String urlEnding = "";
	protected char[] authUsername = null;
	protected char[] authPassword = null;
	protected boolean allowAskForBasicAuth = false;
	
	private boolean createDesktopEntry = false;
	private String desktopWindowsICO = "";
	private String desktopLinuxPNG = "";
	protected String desktopCategories = null;
	protected String desktopKeywords = null;
	protected boolean createPathVariable = false;
	
	protected boolean createIconForDeletion = false;
	protected String iconForDeletionPath = null;
	
	protected boolean isInstallationStarted = false;
	private boolean launchInBackground = true;
	
	private int maxHeapSize = 0;
	
	// ----- //
	
	// systemd settings //
	protected boolean       createUnitFile;
	protected Boolean		startAtBoot;
	protected String		unitDescription;
	protected String 		unitAfter; 
	protected Integer 		unitStartLimitBurst;
	protected Integer 		unitStartLimitInterval;
	protected String 		installWantedBy;
	protected String 		installAliasName;
	protected String		serviceWorkingDir;
	protected String		serviceUser;
	protected String		serviceGroup;
	protected List<String>	serviceEnvironment;
	protected List<String>	serviceExecStartPre;
	protected List<String>  serviceExecStartPost;
	protected Integer 		serviceTimeout;
	protected String 		serviceType;
	protected List<String> 	serviceExecStart;
	protected String 		serviceExecStop;
	protected String 		serviceRestart;
	protected Integer 		serviceRestartSec;
	
	// ----- //
	
	/**
	 * Creates a configuration object for the installation with all the necessary informations 
	 * 
	 * @param company				the name of your company under which the application should be installed
	 * @param version				the version	
	 * @param applicationNameShort	the short name of your application
	 * @param applicationNameLong	the long name of your application
	 */
	public InstallConfig(String company, String version, String applicationNameShort, String applicationNameLong) {
		
		logger = new Logger();
		this.company = company;
		this.version = version;
		this.applicationNameShort = applicationNameShort;
		this.applicationNameLong = applicationNameLong;
	}
	
	// Determine the operating system //
    public enum OSType {
        UNDETERMINED, WINDOWS, LINUX, MACOS
    }
    public static OSType getOsType() {

        final String osName = System.getProperty("os.name", "").toLowerCase();
        
        if (osName.startsWith("windows")) return OSType.WINDOWS;
        else if (osName.startsWith("linux")) return OSType.LINUX;
        else if (osName.startsWith("mac os") || osName.startsWith("macos") || osName.startsWith("darwin")) return OSType.MACOS;

        return OSType.UNDETERMINED;
    } 

   
    /**
    * When the debug mode is enabled, all error messages will be printed out exactly
    * 
    * @param debug	if the debug mode should been enabled
    */
    public void setDebug(boolean debug) { this.debug = debug; }
    protected boolean getDebug() { return debug; }
    
    
    /**
     * If the installation should be executed in the background -{@literal >} no command prompt will appear and prompts will be ignored
     * 
     * @param quiet		Whether to run the installation in the background
     */
    public void setQuiet(boolean quiet) { this.quiet = quiet; }
    protected boolean getQuiet() { return quiet; }
    
	protected String getVersion() { return version; }
	protected String getCompany() { return company; }
	protected String getApplicationNameShort() { return applicationNameShort; }
	protected String getApplicationNameLong() { return applicationNameLong; }
	
	protected Logger getLogger() { return logger; }
	protected boolean getIsUser() { return isUser; }
	protected boolean getIsPortable() { return isPortable; }
	
	
	/**
	 * The given fonts will be installed (the fonts has to be in the format .ttf)
	 * 
	 * @param fonts		a map with all the fonts: the name without .ttf | location of the fonts within the jar file (with .ttf)
	 */
	public void setFontsToInstall(Map<String, String> fonts) {
		this.fontsToInstall = fonts;
	}
	
	protected Map<String, String> getFontsToInstall() { return this.fontsToInstall; }
	
	
	// Executable to install //
	
	/**
	 * Sets the URL of the file to be installed. The executable will be downloaded from the specified URL
	 * 
	 * @param url					the URL
	 * @param basicAuthUser			[ the user for the basic auth ]
	 * @param basicAuthPassword		[ the passwort for the basic auth ]
	 * @param askForBasicAuth		when no basic auth credentials are given and the request gets a 401 response ask the user for credentials at the command line
	 */
	public void setDownloadURLForProgramm(String url, char[] basicAuthUser, char[] basicAuthPassword, boolean askForBasicAuth) {
		this.downloadURL = url;
		this.authUsername = basicAuthUser;
		this.authPassword = basicAuthPassword;
		this.allowAskForBasicAuth = askForBasicAuth;
		
		offline = false;
	}
	
	/**
	 * Sets the URL of the file to be installed. The executable will be downloaded from the specified URL
	 * In addition the operation system and the architecture will be added automatically to the URL (_windows_x64, _linux_arm32)
	 * 
	 * @param url					die URL (without file extension) 
	 * @param basicAuthUser			[ the user for the basic auth ]
	 * @param basicAuthPassword		[ the passwort for the basic auth ]
	 * @param askForBasicAuth		when no basic auth credentials are given and the request gets a 401 response ask the user for credentials at the command line
	 * @param end					die file ending of the file (.jar)
	 */
	public void setDownloadURLForProgramm(String url, char[] basicAuthUser, char[] basicAuthPassword, boolean askForBasicAuth, String end) {
		this.downloadURL = url;
		this.authUsername = basicAuthUser;
		this.authPassword = basicAuthPassword;
		this.urlEnding = end;
		this.addVersion = true;
		this.allowAskForBasicAuth = askForBasicAuth;
		
		offline = false;
	}
	
	/**
	 * Installs the executable from the local file systems instead of downloading the file
	 * 
	 * @param path		the path of the jar file in the file system
	 */
	public void setDownloadOfflinePath(String path) {
		this.downloadURL = path;
		offline = true;
	}
	
	protected boolean getOffline() { return offline; }
	
	
	
	// Creating a desktop entry //
	
	/**
	 * Creates a desktop entry during the installation.
	 * 
	 * - Windows: a entry will be created in the public desktop or in the user desktop directory (for a user installation).
	 *            also an entry in the start menu will be created.
	 * - Linux:   the desktop file will be registered into the start menu.
	 * 
	 * The given picrute will be saved under the program directory under pics/desktop.png / pics/desktop.ico 
	 * 
	 * @param windowsICO	[Windows] the path to the .ico file inside the jar file (resource/pic.ico). The optimum resolution is 256x256
	 * @param linuxPNG		[Linux]   the path of the .png file inside the jar file ...
	 * @param keywords		[Linux]   key-words for the desktop entry
	 * 						          e.g: Spiele;Berichtshefte;Dokumente;Textverarbeitung;
	 */
	public void installDesktopEntry(String windowsICO, String linuxPNG, String keywords) {
		this.createDesktopEntry = true;
		desktopWindowsICO = windowsICO;
		desktopLinuxPNG = linuxPNG;		
		
		createProgramDirs((List<String>) Arrays.asList(new String[] {"pics/"}));
		desktopKeywords = keywords;
	}
	protected String getDesktopWindowsICO() { return desktopWindowsICO; }
	protected String getDesktopLinuxPNG() { return desktopLinuxPNG; }
	protected boolean getCreateDesktopEntry() { return createDesktopEntry; }
	
	/**
	 * [Linux] Sets the categories in which the applications should be present in the menus (desktop entry)
	 * 
	 * @param	categories	the categories separated by a ';' (e.g. Office;AudioVideo;Network;System)
	 */
	public void setDesktopCategories(String categories) { this.desktopCategories = categories; }
	/**
	 * [Linux] Sets the keywords for the application (desktop entry)
	 * @param keywords	the keywords separated by a ';'
	 */
	public void setDesktopKeywords (String keywords) { this.desktopKeywords = keywords; }
	
	
	// Options for the launch of the application //
	
	/**
	 * When the application is launched from the command line, the program will be launched in the foreground normally.
	 * With the parameter --b can the program be launched in the foreground
	 * 
	 * @param runInBackground	whether the program should be launched in background by default -{@literal >} for launching it in the foreground the parameter --f is required
	 */
	public void setRunInBackgroundByDefault(boolean runInBackground) {
		this.launchInBackground = runInBackground;
	}
	protected boolean getRunInBackgroundByDefault() { return launchInBackground; }
	
	/**
	 * [Linux] Creates a unit file for systemd. All the given parameters are optional (except startAtBoot).
	 * For the ..Exec.. parameters and the working directory you can use #~LaunchScript~#, #~AppPath~#, #~ConfigPath~# with a leading slash -{@literal >} /home/myPath/
	 * 
	 * @param startAtBoot			 whether the service should start at boot time
	 * @param unitDescription		 the description of the service
	 * @param unitAfter				 after which target the service should been started
	 * @param unitStartLimitBurst    the maximum number of start retries
     * @param unitStartLimitInterval the interval in seconds in which the maximum number of start retries should been summarized
	 * @param installWantedBy   	 to which time of the boot process the service should been started -{@literal >} multi-user.target (normal) or graphical.target (when GUI is needed)
	 * @param installAliasName  	 an alias for the service name
	 * @param serviceWorkingDir		 the working directory of the service
	 * @param serviceUser			 the user for the service
	 * @param serviceGroup			 the group for the service
	 * @param serviceEnvironment	 the environment variables to set
	 * @param serviceExecStartPre	 the commands to execute before the service starts. Use <LaunchScript> to replace the location of the launch script
	 * @param serviceExecStartPost   the commands to execute after the service has started. Use <LaunchScript> to replace the location of the launch script
	 * @param serviceTimeout		 the number of seconds which should been allowed to start / stop the service
	 * @param serviceType			 the type of the service -{@literal >} oneshot, simple, exec and forking
	 * @param serviceExecStart		 the start command. Please take in mind that only with the type "oneshot" multiple commands can be specified. Use <LaunchScript> to replace the location of the launch script
	 * @param serviceExecStop		 the stop command. Use #LaunchScript# to replace the location of the launch script
	 * @param serviceRestart		 whether the service should been restarted when the execution failed -{@literal >} on-failure or always
	 * @param serviceRestartSec		 the number of seconds to wait between a restart
	 */
	public void createServiceUnitFile(
			boolean startAtBoot, 
			String unitDescription, String unitAfter, Integer unitStartLimitBurst, Integer unitStartLimitInterval,
			String installWantedBy, String installAliasName,
			String serviceWorkingDir, String serviceUser, String serviceGroup, List<String> serviceEnvironment,
			List<String> serviceExecStartPre, List<String> serviceExecStartPost, Integer serviceTimeout, String serviceType,
			List<String> serviceExecStart, String serviceExecStop, String serviceRestart, Integer serviceRestartSec) {
		
		this.createUnitFile			= true;
		this.startAtBoot			= startAtBoot;
		this.unitDescription 		= unitDescription;
		this.unitAfter 				= unitAfter;
		this.unitStartLimitBurst	= unitStartLimitBurst;
		this.unitStartLimitInterval = unitStartLimitInterval;
		this.installWantedBy 		= installWantedBy;
		this.installAliasName 		= installAliasName;
		this.serviceWorkingDir		= serviceWorkingDir;
		this.serviceUser			= serviceUser;
		this.serviceGroup			= serviceGroup;
		this.serviceEnvironment		= serviceEnvironment;
		this.serviceExecStartPre 	= serviceExecStartPre;
		this.serviceExecStartPost 	= serviceExecStartPost;
		this.serviceTimeout 		= serviceTimeout;
		this.serviceType 			= serviceType;
		this.serviceExecStart 		= serviceExecStart;
		this.serviceExecStop 		= serviceExecStop;
		this.serviceRestart 		= serviceRestart;
		this.serviceRestartSec 		= serviceRestartSec;
	}
	
	
	// Removal options //
	
	/**
	 * [Windows] The estimated size of the whole application which should been displayed to the user
	 * 
	 * @param estimatedSize		the estimated size in megabyte
	 */
	public void setEstimatedSize(double estimatedSize) {
		this.estimatedSize = (int) (estimatedSize * 1024);
	}
	/**
	 * [Windows] Returns the setted estimates size of the program
	 * @return	the estimated size in bytes
	 */
	protected int getEstimatedSize() { return estimatedSize; }
	
	/**
	 * [Windows] Icon for the removal of the application.
	 * A file (pics/uninstall.ico) will be created.
	 * 
	 * @param windowsICO	the path of the .ico file inside of the jar file (e.g. resource/uninstall.ico)
	 */
	public void setIconForWindowsUninstaller(String windowsICO) {
		this.createIconForDeletion = true;
		this.iconForDeletionPath = windowsICO;
	}

	
	// Options for the installations //
    
	/**
	 * Installs the program portable
	 * 
	 * @param dir 	the main directory for the portable installation (e.g. C:/Users/de03710/MyProgram/)
	 */
	public void setPortable(String dir) {
		
		this.isPortable = true;
		this.portableMainDir = dir;
		this.configDir = dir + "Appdata/";
		this.applicationDir = dir + "Programm/";
		
		isPortable = true;
		
		initConfigDir();
		initApplicationDir();
	}
	
	/**
	 * [Windows] Installs the program only for the actual user -{@literal >} administrator rights aren't necessary
	 */
	public void setUserInstallation() {
		
		if (InstallConfig.getOsType() != OSType.WINDOWS) return;
		
		this.applicationDir = System.getenv("LOCALAPPDATA").replace("\\", "/") + "/" + this.getCompany() + "/" + this.getApplicationNameShort() + "/";
		isUser = true;
	}
	
	/**
	 * [Windows] creates a entry in the path variable for the program
	 * TODO: create Launcher wie unter Linux
	 */
	public void createPathEntry() {
		createPathVariable = true;
	}
	
	
	// Determine and create the application paths //
	
	/**
	 * Returns the path of the desktop
	 * 
	 * @return desktop path: /home/user/Desktop/ or C:/User/myUserName/Desktop/.
	 *		   when no path could been determined, null will be returned
	 */
	protected String getDesktopDir() {
		
		// checks if the path has already been determined -> return the path instantly
		if (this.desktopDir != null && !this.desktopDir.equals("")) return this.desktopDir;
		
		String rtc = System.getProperty("user.home");
		
		if (System.getProperty("os.name").toLowerCase().contains("windows")) {
			rtc = rtc.replace("\\", "/");
			rtc += "/Desktop/";
			
		} else if (System.getProperty("os.name").toLowerCase().contains("nux")) {
			try {
				// under linux the desktop path isn't always identical
				String command = "test -f ${XDG_CONFIG_HOME:-~/.config}/user-dirs.dirs && "
				 		+ "source ${XDG_CONFIG_HOME:-~/.config}/user-dirs.dirs;"
				 		+ "echo ${XDG_DESKTOP_DIR:-$HOME/Desktop}";

				Process p = new ProcessBuilder("bash", "-c", command).start();

				p.waitFor();
				BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
				String line = "";
				String output = "";

				while ((line = buf.readLine()) != null) { output += line; }
				
				if (output.toLowerCase().contains(System.getProperty("user.name").toLowerCase())) {
					rtc = output + "/";
				}
			} catch (Exception ex) {
				logger.log("w", "Location of Desktop could not be determed. Using default Location: Desktop", "getDesktopDir");
				rtc += "/Desktop/";
			}
			
		} else {
			logger.log("w", "Plattform is not supported", "getDesktopDir"); return null;
		}
		
		this.desktopDir = rtc;
		return rtc;
	}
	
	
	/**
	 * Returns the configuration directory of the application: /home/user/.config/ or C:\Users\User\AppData\
	 * If the path isn't existing, it will be created
	 * 
	 * @return the path of the directory: /home/user/.config/Company/ShortName/
	 */
	public String getConfigDir() {
		
		// checks if the path has already been determined -> return the path instantly
		if (this.configDir != null && !this.configDir.isBlank()) return this.configDir;
		
		String rtc = "";
		
		if (InstallConfig.getOsType() == OSType.WINDOWS) {
			rtc = System.getenv("AppData").replace("\\", "/") + "/" + this.getCompany() + "/" + this.getApplicationNameShort() + "/";
		} else if (InstallConfig.getOsType() == OSType.LINUX) {
			rtc = System.getProperty("user.home") + "/.config/" + this.getCompany() + "/" + this.getApplicationNameShort() + "/";
		}
		
		this.configDir = rtc;
		this.initConfigDir();

		return rtc;
	}
	
	/**
	 * Creates the given directory in the configuration directory
	 * 
	 * @param directorys	a list with all the directories to create. This are relative paths -{@literal >} logs/ or config/
	 */
	public void createConfigDirs(List<String> directorys) {
		
		directorysInConfig.addAll(directorys);
		directorysInConfig.add("");
		initConfigDir();
	}
	
	private void initConfigDir() {
		
		if (!isInstallationStarted) return; 	// before the start of the installation no folders will be created
		
		String rtc = this.configDir;
		
		try {
			for (String direcotory: directorysInConfig) {
				File currentDirectory = new File(rtc + direcotory);
				if (!currentDirectory.exists()) new File(rtc + direcotory).mkdirs();
			}
		} catch (Exception ex) { logger.log("e", ex, "getConfigDir"); }
	}
	
	
	
	/**
	 * Returns the main directory of the application. If the directory doesn't exist, it will be created
	 * 
	 * @return the path of the application dir: C:/Program Files/Company/MyProgram/
	 */
	public String getApplicationDir() {
		
		// checks if the path has already been determined -> return the path instantly
		if (this.applicationDir != null && !this.applicationDir.equals("")) return this.applicationDir;
		
		String rtc = "";
		
		if (InstallConfig.getOsType() == OSType.WINDOWS) {
			// even though the arch is 64 bit it doesn't mean that the JRE is also installed in the 64 bit variant -> check the installed architecture of the JRE
			// @Todo: WHAT HAPPENS, IF THE JRE IS BEEN UPDATED -> THE CORRECT PATH CAN'T BE DETERMINED ANYMORE??
			if (System.getProperty("os.arch").endsWith("86")) rtc += System.getenv("PROGRAMFILES").replace("\\",  "/");
			else rtc += System.getenv("ProgramW6432").replace("\\",  "/");
			rtc += "/" + this.getCompany() + "/" + this.getApplicationNameShort() + "/";
		} else if (InstallConfig.getOsType() == OSType.LINUX) {
			rtc = "/usr/share/" + this.getCompany() + "/" + this.getApplicationNameShort() + "/";
		}
		
		this.applicationDir = rtc;
		initApplicationDir();
		
		return rtc;
	}
	
	/**
	 * Creates the given directory in the application directory
	 * 
	 * @param directorys	a list with all the directories to create. This are relative paths -{@literal >} logs/ or config/
	 */
	public void createProgramDirs(List<String> directorys) {
		
		this.directorysInAppData.addAll(directorys);
		directorysInAppData.add("");
		initApplicationDir(); 	// isn't done right here -> directories will be created before the installation and not now
	}
	
	protected void initApplicationDir() {
		
		if (!isInstallationStarted) return; 	// before the installation no directory is been created
		
		// if an path entry should be added (only for windows) the path variable points to an own folder
		if (InstallConfig.getOsType() == OSType.WINDOWS && this.createPathVariable && !directorysInAppData.contains("path/")) {
			directorysInAppData.add("path/");
		}
		
		String rtc = this.applicationDir;
		
		try {
			for (String directory: directorysInAppData) {
				File currentDirectory = new File(rtc + directory);
				if (!currentDirectory.exists()) new File(rtc + directory).mkdirs();
			}
		} catch (Exception ex) { logger.log("e", ex, "initApplicationDir"); }
	}
	
	/**
	 * Returns the main directory of the portable installation
	 * 
	 * @return the path: C:/Users/de03710/RPdb/
	 */
	protected String getPortableDir() {
		return this.portableMainDir;
	}
	
	/**
	 * Extracts a file from the jar file and copy it to the given path
	 * 
	 * @param pathInJar 	the path in the jar file to extract: resource/48x48.png
	 * @param pathToWrite 	the destination path
	 * @param logError 		if an error message should be displayed
	 * 
	 * @return 				if the resource was successfully extracted
	 */
	protected boolean getResource(String pathInJar, String pathToWrite, boolean logError) {
		
		final File jarFile = new File(getLocationOfJarFile());

		try {
			if (jarFile.isFile()) {
				InputStream in = getClass().getResourceAsStream("/" + pathInJar); 
				FileUtils.copyInputStreamToFile(in, new File(pathToWrite));
				return true;
		} 
			
			return false;

		} catch (Exception ex ) { 
			if (logError) logger.log("w", ex, "getResource");
			return false;
		}
		
	}
	
	/**
	 * Extracts a file from the jar file and copy it to the given path
	 * 
	 * @param pathInJar 	the path in the jar file to extract: resource/48x48.png
	 * @param pathToWrite 	the destination path
	 * 
	 * @return 				if the resource was sucessfully extracted
	 */
	protected boolean getResource(String pathInJar, String pathToWrite) {
		return getResource(pathInJar, pathToWrite, true);	
	}
	
	
	/**
	 * Returns the path to the actual jar file 
	 * 
	 * @return the absolute path to the jar file: C:/Users/myUserName/BeMa.jar.
	 * 		   When no jar file was found (when launched in Eclipse) the path to the "extracted" jar file will be returned
	 */
	protected String getLocationOfJarFile() {
		
		if (this.jarRuntimeLocation != null && !this.jarRuntimeLocation.equals("")) return this.jarRuntimeLocation;
		
		String location;
		
		try {
			location = new File(getClass().getProtectionDomain().getCodeSource().getLocation().toURI().getPath()).getAbsolutePath();
		} catch (URISyntaxException ex) {
			logger.log("w", ex, "getLocationOfJarFile");
			location = new File(getClass().getProtectionDomain().getCodeSource().getLocation().getPath()).getAbsolutePath();
		}
		
		this.jarRuntimeLocation = location;
		return location;
	}
	
	/**
	 * Sets the path to the jar runtime path -{@literal >} overwrite for the portable installation
	 * 
	 * @param location		the absolute path to the jar file: C:/Users/de03710/BeMa.jar
	 */
	protected void setLocationOfJarFile(String location) {
		this.jarRuntimeLocation = location;
	}	
	
	/**
	 * Sets the maximum heap size the JVM may consume (-Xmx)
	 * @param sizeInMb	the maximum size in megabye
	 */
	public void setMaxHeapSize(int sizeInMb) {
		if (sizeInMb < 2) logger.log("w", "The maximum heap size must be greater or equal 2 megabyte", "setMaxHeapSize");
		else this.maxHeapSize = sizeInMb;
	}
	
	protected int getMaxHeapSize() { return maxHeapSize; }
	
}

package de.rpjosh.installer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.ObjectInputFilter.Config;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.prefs.Preferences;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;

import mslinks.ShellLink;
import de.rpjosh.installer.InstallConfig.OSType;

import static java.lang.System.setErr;
import static java.util.prefs.Preferences.systemRoot;

public class Installer {

	private InstallConfig conf;
	private Data data;
	
	public int error = 0;
	
	public Installer(InstallConfig conf) {
		this.conf = conf;
		this.data = conf.getData();
	}
	
	/**
	 * Starts the installation of the program
	 * 
 	 * @param args 		If the program has to be restarted you can specify here the parameters with which the program should been restarted.
 	 * 					These are normally the parameters which were specified when launching your installer
	 */
	public void installProgramm(String[] args) {
		
		conf.isInstallationStarted = true;
		
		// überprüfe, ob Root rechte vorhanden sind
		if (!conf.getIsPortable() && !conf.getIsUser()) {
			if (!this.checkRoot()) {
				System.out.println("Zur Installation dieses Programm werden Administrator / Root Rechte benötigt.\n\n"
						+ "Falls du keine solchen Rechte hast, kann das Programm auch portable oder nur für diesen Benutzer installiert werden.\n"
						+ "Für eine weitere Hilfe führe dieses Programm mit dem Parameter --help aus.");
				error = -1;
				
				if ( (InstallConfig.getOsType() == OSType.WINDOWS || InstallConfig.getOsType() == OSType.LINUX) && !conf.getQuiet()) {
					System.out.print("\nFalls du doch solche Rechte hast, kann das Programm automatisch versuchen, die Installation mit Administratorrechten zu starten.\n"
							+ "Möchtest du das Programm mit Administratorrechten neustarten (Y/N)?: ");
				
					try {
						BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
						long startTime = System.currentTimeMillis();
											
						// after a timeout of 15 seconds the program will be closed
						while ((System.currentTimeMillis() - startTime) < 15 * 1000 && !in.ready()) {
						}
						
						if (in.ready()) {
							if (in.readLine().toLowerCase().startsWith("y")) {
								RunInConsole.start(args, true, true, true);
							}
						}
						
						error = -2; return;
						
					} catch (Exception ex) { data.log("e", ex, "installProgramm"); 
						error = -3; return;
					}
				}
				
				error = -4; return;
			}
		}
		
		try { Thread.sleep(10); } catch (Exception ex) { }
		
		if (conf.getIsUser() && InstallConfig.getOsType() != OSType.WINDOWS) { System.out.println("A user installation is only available under windows"); error = -10; }
		
		System.out.println("\nStarting the installation of " + conf.getApplicationNameShort() + " (version " + conf.getVersion() + ").\n");
		
		// all running instances will be killed
		this.killRunningInstances();
		
		System.out.print("Determine architekture and operating system: ");
		String aarch = this.getVersionOfProgramm();
		System.out.println(aarch);
		
		// copies or downloads the file
		if (conf.downloadURL == null) {
			data.log("w", "no file to download or copy specified", "installProgramm");
			error = -5;
			return;
		}
		
		String jarFile = "";
		
		if (!conf.getOffline()) {
			System.out.print("Downloading file: ");
			jarFile = this.downloadFile(conf.downloadURL, conf.addVersion, conf.urlEnding);
			if (error < 0) return;
			System.out.println("\rDownloading file: successful downloaded");
		} else {
			File fileOffline = new File(conf.downloadURL);
			
			if (!fileOffline.exists() || fileOffline.length() < ( 1024 * 1024)) {
				System.out.println("The given file is invalid!");
				error = -11; return;
			}
			jarFile = fileOffline.getAbsolutePath();
		}
		
		if (conf.getIsPortable()) {
			System.out.println("\nProgram will be installed portable under: " + conf.getPortableDir());
			
			File portableDir = new File(conf.getPortableDir());
			if (!portableDir.exists()) {
				System.out.print("Directory does not exist. Creating directory: ");
				if (!portableDir.mkdirs()) {
					System.out.print("No authorization!");
					error = -12; return;
				} else System.out.print("created");
			}
			
			conf.setPortable(portableDir.getAbsolutePath().replace("\\", "/") + "/");	
		}
				
		System.out.print("\nCopy jar file: ");
		try {
			FileUtils.copyInputStreamToFile(new FileInputStream(new File(jarFile)), new File(conf.getApplicationDir() + conf.getApplicationNameShort() + ".jar"));
		} catch (Exception ex) {
			System.out.println("failed.\n\nError message: ");
			ex.printStackTrace();
			error = -13; return;
		}
		System.out.println("successful\n");
		
		// sets the path for the jar file for the shortcuts
		conf.setLocationOfJarFile(conf.getApplicationDir() + conf.getApplicationNameShort() + ".jar");
		
		// Icon für die Systemsteuerung für die Deinstallation setzen -> wird immer gesetzt
		if (conf.createIconForDeletion) {
			conf.createProgramDirs(new ArrayList<String>() {{  add("pics/"); }});
			conf.getResource(conf.iconForDeletionPath, conf.getApplicationDir() + "pics/uninstall.ico");
		}
		
		if (conf.getIsPortable()) {
			
			System.out.print("Creating required files: ");
			try {
				// creates a file "portable" in the application directory //
				FileWriter fw = new FileWriter(conf.getApplicationDir() + "portable");
				PrintWriter pw = new PrintWriter(fw);
				pw.println("This file is needed that the program recognizes, that the application is installed portable");
				pw.println("Therefore please do not delete this inconspicuous file!");
				pw.flush();
				pw.close();
				
				if (InstallConfig.getOsType() == OSType.LINUX) {
					// create a launch script
					this.createLauncher("Programm/" + conf.getApplicationNameShort() + ".jar", conf.getPortableDir() + conf.getApplicationNameShort(), false);
					
					//Datei ausführbar machen
					Process p = new ProcessBuilder("bash", "-c", "chmod +x " + conf.getPortableDir() + conf.getApplicationNameShort()).start();
					p.waitFor(5000, TimeUnit.SECONDS);
					
					this.createDesktopShortcut(conf.getPortableDir() + conf.getApplicationNameShort() + ".desktop", "");
				} else if (InstallConfig.getOsType() == OSType.WINDOWS) {
					this.createDesktopShortcut(conf.getPortableDir() + conf.getApplicationNameShort() + ".lnk", "");
				}

				System.out.println("erfolgreich");

			} catch (Exception ex) { System.out.println("fehlgeschlagen"); error = -14; return; }
			
		} else if (conf.getIsUser()) {
			// erstelle eine Textdatei User im Programmverzeichnis, damit erkenbar ist, dass das Programm nur für den aktuellen Benutze instsalliert worden ist. //
			try {
				FileWriter fw = new FileWriter(conf.getApplicationDir() + "userInstallation");
				PrintWriter pw = new PrintWriter(fw);
				pw.println("This file is needed that the program recognizes, that the application is installed only for a specific user");
				pw.println("Therefore please do not delete this inconspicuous file!");
				pw.flush();
				pw.close();
				System.out.println("Execute other commands...");
				this.registerApplication(conf.getIsUser());
			} catch (Exception ex) { System.out.println("Creation of the files failed..."); error = -15; return; }

		} else {
			System.out.println("Execute other commands...");
			
			// the program will be created
			this.registerApplication(conf.getIsUser());
		}
		
		this.installFonts();
		
		if (error < 0) return;
		
		System.out.println("\nInstallation was completed successfully\n");
		
	}
	
	/**
	 * Beendet alle noch möglicherweise laufenden Instanzen des Programms (z.B. bei einem Update)
	 */
	private void killRunningInstances() {
		
		try {
			
			if (InstallConfig.getOsType() == OSType.WINDOWS) {
				Process p = new ProcessBuilder("cmd.exe", "/C", "wmic PROCESS Where \"name Like '%java%' AND CommandLine like '%" + conf.getApplicationNameShort() + ".jar%'\" Call Terminate").start();
				p.waitFor(5, TimeUnit.SECONDS);
				
			} else if (InstallConfig.getOsType() == OSType.LINUX) {
				Process p = new ProcessBuilder("bash", "-c", "pkill -9 -f '" + conf.getApplicationNameShort() + ".jar'").start();
				p.waitFor(5, TimeUnit.SECONDS);
			}
		} catch (Exception ex) { /* nicht nötig */ }

	}

	
	/**
	 * Erstellt eine Verknüpfung zur Jar-Datei
	 * @param target Wo die Verknüpfung erstellt werden soll, sowie dessen Dateinamen (z.B. /home/user/Desktop/BeMa.desktop oder C:/User/de03710/Desktop/hi.moin)
	 * @param args zusätzliche Parameter wie -u hi -p secret -w
	 */
	private void createDesktopShortcut(String target, String args) {
		
		if (!conf.getCreateDesktopEntry()) return;
		
		String iconPath = conf.getApplicationDir() + "pics/";

		try {
			if ( InstallConfig.getOsType() == OSType.WINDOWS) {
				
				conf.getResource(conf.getDesktopWindowsICO(), iconPath + "desktop.ico");
				
				ShellLink sl = ShellLink.createLink(conf.getApplicationDir() + conf.getApplicationNameShort() + ".bat").setIconLocation(iconPath.replace("/", "\\") + "desktop.ico");
				sl.setCMDArgs(args);
				sl.saveTo(target);
			
			} else if (InstallConfig.getOsType() == OSType.LINUX) {
				
				conf.getResource(conf.getDesktopLinuxPNG(), iconPath + "desktop.png");
				
				String desktopEntry = ""
						+ "[Desktop Entry]" + "\n"
						+ "Encoding=UTF-8" + "\n"
						+ "Name=" + conf.getApplicationNameShort() + "\n"
						+ "Name[de]=" + conf.getApplicationNameShort() + "\n"
						+ "Exec=\"" + ( conf.getIsPortable() ? (conf.getPortableDir()) + conf.getApplicationNameShort() : ("/usr/bin/" + conf.getApplicationNameShort()) ) + "\" " + args + "\n"
						+ "Terminal=false" + "\n"
						+ "Type=Application" + "\n"
						+ "Icon=" + iconPath + "desktop.png" + "\n";
						
						if (conf.desktopCategories != null) 	desktopEntry += "Categories=" + conf.desktopCategories;
						if (conf.desktopKeywords != null) 		desktopEntry += "Keywords=" + conf.desktopKeywords;
				
				FileWriter fwFile = new FileWriter(target);
				PrintWriter pw = new PrintWriter(fwFile);	
				
				pw.print(desktopEntry);
				pw.flush();
				pw.close();
			}
				
		} catch (Exception ex) {
			data.log("e", ex, "createDesktopShortcut"); 
		}
	}
	
	
	private void registerApplication(Boolean userInstallation) {
		
		if (InstallConfig.getOsType() == OSType.WINDOWS) {
			
			// Erstellung eines Links, damit das Proramm von überall aufgerufen werden kann //
			this.createLauncher("", "", true);
			// create also an link for the %PATH% variable
			if (conf.createPathVariable) this.createLauncher("", conf.getApplicationDir() + "path/" + conf.getApplicationNameShort() + ".bat", true);

			
			// zuerst wird eine Dekstopverknüpfung erstellt //
			if (conf.getCreateDesktopEntry()) {
				
				if (userInstallation) {
					String destination = conf.getDesktopDir();
					destination += conf.getApplicationNameShort() + ".lnk"; 
					createDesktopShortcut(destination, "");	
				} else {
					// es wird eine Verknknüpfung im Öffentlichen Desktop erstellt
					String destination = System.getenv("public").replace("\\", "/") + "/Desktop/";
					destination += conf.getApplicationNameShort() + ".lnk"; 
					createDesktopShortcut(destination, "");	
				}
				
				// im nächsten Schritt wird eine Verknüpfung in das Startmenü aufgenommenn //
				String locationStartMenu = "";
				if (userInstallation) {
					locationStartMenu = System.getenv("APPDATA").replace("\\", "/") + "/Microsoft/Windows/Start Menu/Programs/";
				} else {
					locationStartMenu = System.getenv("ALLUSERSPROFILE").replace("\\", "/") + "/Microsoft/Windows/Start Menu/Programs/";
				}

				if (!new File(locationStartMenu).exists()) data.log("w", "Start Menu folder \"" + locationStartMenu + "\" does not exist!", "registerApplication");
				else {
					locationStartMenu += conf.getCompany() + "/";
					new File(locationStartMenu).mkdirs();
					
					locationStartMenu += conf.getApplicationNameShort() + ".lnk";				
					this.createDesktopShortcut(locationStartMenu, "");
				}
				
			}
			
			// es muss noch ein uninstall Schlüssel in die Registry geschrieben werden, um über die Systemsteuerung alles deinstallieren zu können //
			String iconPath = conf.getApplicationDir() + "pics/uninstall.ico";
			String locationRegistry = "";
			
			if (userInstallation) locationRegistry += "HKEY_CURRENT_USER";
			else locationRegistry += "HKEY_LOCAL_MACHINE";
			
			locationRegistry += "\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\Uninstall\\" + conf.getApplicationNameShort();
			String batchFile = 
					    "@echo off\n"
					  + "reg add \"" + locationRegistry + "\" /v DisplayIcon /t REG_SZ /d \""+ iconPath.replace("/", "\\") + "\" /f \n"
					  + "reg add \"" + locationRegistry + "\" /v DisplayName /t REG_SZ /d \"" + conf.getApplicationNameLong() + "\" /f \n"
					  + "reg add \"" + locationRegistry + "\" /v DisplayVersion /t REG_SZ /d \"" + conf.getVersion() + "\" /f \n"
					  + "reg add \"" + locationRegistry + "\" /v estimatedSize /t REG_DWORD /d \"" + conf.getEstimatedSize() + "\" /f \n"
					  + "reg add \"" + locationRegistry + "\" /v InstallLocation /t REG_SZ /f /d \"" + conf.getApplicationDir().replace("/", "\\") + "\" \n"
					  + "reg add \"" + locationRegistry + "\" /v Publisher /t REG_SZ /d \"" + conf.getCompany() + "\" /f \n"
					  + "reg add \"" + locationRegistry + "\" /v UninstallString /t REG_SZ /d \"" + conf.getApplicationDir().replace("/", "\\") + "uninstall.bat\" /f \n";
			
			// additional an entry in the path variable will be added (for start RPdb and in the "real" %PATH% variable)
			String locationRegistryPath = "";
			if (conf.createPathVariable) {
				
				// adding support for "start RPdb"
				locationRegistryPath += "$REGISTRYPATH$" + conf.getApplicationNameShort() + ".exe";
				String batchFilePath = 
						"reg add \"" + locationRegistryPath + "\" /f \n"
					  + "reg add \"" + locationRegistryPath + "\" /ve /f /d \"" + conf.getApplicationDir().replace("/", "\\") + conf.getApplicationNameShort() + ".bat\" \n"	// ersetzt den (Default) Wert mit den korrekten Pfad
					  + "reg add \"" + locationRegistryPath + "\" /v Path /t REG_SZ /f /d \"" + conf.getApplicationDir().replace("/", "\\") + "\" \n";
				
				// this creates an entry for the whole machine as well as for the user
				if (userInstallation)  batchFile += batchFilePath.replace("$REGISTRYPATH$", "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\");
				if (!userInstallation) batchFile += batchFilePath.replace("$REGISTRYPATH$", "HKEY_LOCAL_MACHINE\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\");
			
				// expanding the path variable
				// @TODO maybe we should edit the path variable in the registry directly HKEY_LOCAL_MACHINE\SYSTEM\CurrentControlSet\Control\Session Manager\Environment to overcome the 1024 max lenght limit
				batchFile +=
						  "setlocal EnableDelayedExpansion\n"
						+ "set \"SEARCHTEXT=;" + conf.getApplicationDir().replace("/", "\\") + "path;\"\n"
						+ "set \"REPLACETEXT=;\"\n";
				if (userInstallation)  batchFile +=
					  "for /F \"skip=2 tokens=1,2*\" %%N in ('%SystemRoot%\\System32\\reg.exe query \"HKCU\\Environment\" /v \"Path\" 2^>nul') do if /I \"%%N\" == \"Path\" call set \"UserPath=%%P\"\n"
					+ "set \"newText=!UserPath:%SEARCHTEXT%=%REPLACETEXT%!\"\n"
					+ "if \"!UserPath!\" == \"%newText%\" ( setx PATH \"%UserPath%;" + conf.getApplicationDir().replace("/", "\\") + "path;\")\n";
				if (!userInstallation) batchFile += 
					  "set \"newText=!PATH:%SEARCHTEXT%=%REPLACETEXT%!\"\n"
					+ "if \"%PATH%\" == \"%newText%\" ( setx /M PATH \"%PATH%;" + conf.getApplicationDir().replace("/", "\\") + "path;\")\n";
				batchFile += "endlocal\n";
			}

			try {
				// es wird die Batch-Datei erstellt, und anschließend ausgeführt
				File batchMakeRegeditEntry = File.createTempFile("installApplication", ".bat");
				
				FileWriter fwFile = new FileWriter(batchMakeRegeditEntry);
				PrintWriter pwFile = new PrintWriter(fwFile);
				
				pwFile.print(batchFile);
				pwFile.flush();
				pwFile.close();

				Process p = new ProcessBuilder("cmd.exe", "/C", batchMakeRegeditEntry.getAbsolutePath()).start();
				if (!p.waitFor(10, TimeUnit.SECONDS)) data.log("w", "Batch File which adds some Registry Keys timed out", "registerApplication");
				
			} catch (Exception ex) {
				data.log("e", ex, "registerApplication (make regedit Entry)");
			}
			
			// nun muss noch ein uninstall Skript zur Verfügung gestellt werden //
			String batchFileUninstall = "@echo off \n"
					+ "wmic PROCESS Where \"name Like '%%java%%' AND CommandLine like '%%" + conf.getApplicationNameShort() + "%%'\" Call Terminate \n";
			if (userInstallation) batchFileUninstall += "del \"%LOCALAPPDATA%";
			else				  batchFileUninstall += "del \"%ProgramData%";
			batchFileUninstall +=
					  "\\Microsoft\\Windows\\Start Menu\\Programs\\" + conf.getCompany() + "\\" + conf.getApplicationNameShort() + "*\" /q \n"
					+ "rd \"" + conf.getConfigDir().replace("/", "\\") + "\" /q /s \n"
					+ "reg delete \"" + locationRegistry + "\" /f \n"
					+ "del \"" + conf.getDesktopDir().replace("/", "\\") + conf.getApplicationNameShort() + ".lnk\" /q \n"
					+ "del \"" + conf.getDesktopDir().replace("/", "\\") + conf.getApplicationNameShort() + " - *.lnk\" /q \n"
					+ "del \"%public%\\Desktop\\" + conf.getApplicationNameShort() + ".lnk\" /q \n";
			
			if (conf.createPathVariable) {
				if (userInstallation)  batchFileUninstall += "reg delete \"" + locationRegistryPath.replace("$REGISTRYPATH$", "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\") + "\" /f \n";
				if (!userInstallation) batchFileUninstall += "reg delete \"" + locationRegistryPath.replace("$REGISTRYPATH$", "HKEY_CURRENT_USER\\SOFTWARE\\Microsoft\\Windows\\CurrentVersion\\App Paths\\") + "\" /f \n";
				
				// removing path entry
				batchFileUninstall +=
						  "setlocal EnableDelayedExpansion\n"
						+ "set \"SEARCHTEXT=;" + conf.getApplicationDir().replace("/", "\\") + "path;\"\n"
						+ "set \"REPLACETEXT=;\"\n";
				if (userInstallation)  batchFileUninstall +=
					  "for /F \"skip=2 tokens=1,2*\" %%N in ('%SystemRoot%\\System32\\reg.exe query \"HKCU\\Environment\" /v \"Path\" 2^>nul') do if /I \"%%N\" == \"Path\" call set \"UserPath=%%P\"\n"
					+ "set \"newText=!UserPath:%SEARCHTEXT%=%REPLACETEXT%!\"\n"
					+ "setx PATH \"%newText%\"\n";
				if (!userInstallation) batchFileUninstall += 
					  "set \"newText=!PATH:%SEARCHTEXT%=%REPLACETEXT%!\"\n"
					+ "setx /M PATH \"%newText%\"\n";
				batchFileUninstall += "endlocal  \n";
			}
					
			batchFileUninstall += "rd \"" + conf.getApplicationDir().replace("/", "\\") + "\" /q /s \n"
					+ "echo Deinstalationsroutine beendet!\n"
					+ "pause\n";
		
			try {
				// es wird die Batch-Datei erstellt
				File uninstallFile = new File(conf.getApplicationDir() + "uninstall.bat");
				
				FileWriter fwFile = new FileWriter(uninstallFile);
				PrintWriter pwFile = new PrintWriter(fwFile);
				
				pwFile.print(batchFileUninstall);
				pwFile.flush();
				pwFile.close();

			} catch (Exception ex) {
				data.log("e", ex, "registerApplication (make uninstall Skript)");
			}
			
		}
		
		if (InstallConfig.getOsType() == OSType.LINUX) {
			
			// keine Erstellung einer Verknüpfung, da Installer als root ausgeführt wird //
			
			if (!checkRoot()) { data.log("i", "Installer has to be run as a root user", "registerApplication" ); return; }
			
			// Erstellung einer Verknüpfung für das StartMenü //
			if (conf.getCreateDesktopEntry()) {
				String pathMenu = "/usr/share/applications/" + conf.getApplicationNameShort() + ".desktop";
				this.createDesktopShortcut(pathMenu, "");
			}
			
			// Erstellung eines Links, damit das Proramm von überall aufgerufen werden kann //
			this.createLauncher("", "", true);
			
			// Create a systemd unit file //
			if (conf.createUnitFile) this.createUnitFile();
			
			// Create a uninstaller //
			try {
				String batchFileUninstall = "#!/bin/bash" + "\n"
						+ "keepUserSettings=false" + "\n"
						+ "\n"
						+ "userID=\"$(id -u)\"" + "\n"
						+ "if [ \"$userID\" != \"0\" ]; then echo \"The uninstaller has to be run as root!\"; exit -1; fi" + "\n"
						+ "\n"
						+ "if [ \"$#\" -gt 0 ]; then " + "\n"
						+ "    if [ \"$1\" = \"-k\" ] || [ \"$1\" = \"--keep-Settings\" ]; then keepUserSettings=\"true\"" + "\n"
						+ "    else " + "\n"
						+ "        echo -e \"Parameter: \\n \\" + "\n"
						+ "            -k \\t --keep-Setting  \\t Benutzereinstellungen werden nicht gelöscht\"" + "\n"
						+ "    fi" + "\n"
						+ "fi" + "\n"
						+ "\n"
						+ "if [ \"$keepUserSettings\" = \"false\" ]; then " + "\n"
						+ "   echo -n \"Alle Benutzereinstellungen werden gelöscht. Um dies zu verhinden, dürcke innerhalb der nächsten 8 Sekunden eine Taste: \"" + "\n"
						+ "   read -n1 -t 8 tmp; if [ \"$tmp\" != \"\" ]; then keepUserSetting=\"true\"; fi" + "\n"
						+ "fi" + "\n"
						+ "\n"
						+ "\n"
						+ "rm -f /usr/bin/" + conf.getApplicationNameShort() + "\n"
						+ "rm -f /usr/share/applications/" + conf.getApplicationNameShort() + ".desktop" + "\n"
						+ "\n"
						//es müssen alle Benutzereinstellungen aller im System eingetragenen Benutzer gelöscht werden (PID > 1000 + PID < 6000)
						+ "directorys=\"$(awk -F: '($3 >= 1000 && $3 < 6000) {printf \"%s;\",$6}' /etc/passwd)\"" + "\n"
						+ "directorys2=\"$directorys2\"" + "\n"
						+ "\n"
						+ "if [ \"$keepUserSettings\" = \"false\" ]; then" + "\n"
						+ "    while [ -n \"$directorys\" ]; do" + "\n"
						+ "        directorys2=\"${directorys%%;*}\"" + "\n"
						+ "        [ \"$directorys\" = \"${directorys/;/}\" ] && directorys= || directorys=\"${directorys#*;}\"" + "\n"
						+ "        \n"
						+ "        rm -r -f \"$directorys2\"/.config/" + conf.getCompany() + "/" + conf.getApplicationNameShort() + "\n"
						+ "    done" + "\n"
						+ "fi" + "\n"
						+ "pkill -9 -f '" + conf.getApplicationNameShort() + ".jar'" + "\n"
						+ "rm -r -f " + conf.getApplicationDir() + "\n";
				
				// remove systemd entry
				if (conf.createUnitFile) {
					batchFileUninstall 
					   += "rm -f \"" + "/etc/systemd/system/" + conf.getApplicationNameShort() + ".service\"" + "\n"
				        + "systemctl stop " + conf.getApplicationNameShort() + ".service" + "\n"
					    + "systemctl daemon-reload" + "\n"
					    + "\n";
				}
				
				batchFileUninstall += "\necho \"\"";
				
				String destination = conf.getApplicationDir() + "uninstall.sh";
				File uninstaller = new File(destination);
				
				FileWriter fwFile = new FileWriter(uninstaller);
				PrintWriter pwFile = new PrintWriter(fwFile);
				
				pwFile.print(batchFileUninstall);
				pwFile.flush();
				pwFile.close();
				
				Process p = new ProcessBuilder("bash", "-c", "chmod +x " + conf.getApplicationDir() + "uninstall.sh").start();
				p.waitFor(5000, TimeUnit.SECONDS);
				
			} catch (Exception ex) {
				data.log("e", ex, "registerApplication (create Uninstall-Skript");
			}
			
		}
	}
	
	/**
	 * Erstellt einen ausführbaren Link zum launcher unter Linux und Windows (unter Windows wird dieses auch in path folder gelegt.
	 * @param pathToLink Der Pfad zur .jar Datei. Ohne eine angabe wird der Aktuelle Pfad der Jar-Datei genommen
	 * @param pathToCreate Wo die Datei abgelegt werden soll. Standardmäßig wird /usr/bin/nameOfProgram genommen
	 * @param uninstaller Ob auch eine Uninstall-Option implementiert werden soll
	 */
	private void createLauncher(String pathToLink, String destination, boolean uninstaller) {
		
		if (pathToLink == null || pathToLink.equals("")) pathToLink = conf.getLocationOfJarFile();
		
		if (InstallConfig.getOsType() == OSType.LINUX) {
			if (destination == null || destination.equals("")) destination = "/usr/bin/" + conf.getApplicationNameShort();
			
			try {
				String batchFileLink = 
							"#!/bin/bash" + "\n"
							+ "\n"
							+ "foreground=\"" + (conf.getRunInBackgroundByDefault() ? "falseDefault" : "trueDefault" ) + "\"" + "\n"
							+ "stop=\"false\"" + "\n"
							+ "uninstall=\"false\"" + "\n"
							+ "programOptions=\"\"" + "\n"
							+ "\n"
							+ "printHelp() {" + "\n"
							+ "    echo -e \\" + "\n"
							+ "    \"\\nSyntax: " + conf.getApplicationNameShort() + " [options] [Programmparameter] \\" + "\n"
							+ "    \\nOptionen:\\n \\" + "\n"
							+ "    --f      ---foreground        \\t Bei angabe wird das Programm im Vordergrund ausgeführt " + (conf.getRunInBackgroundByDefault() ? "" : "(default)") +  "\\n \\" + "\n"
							+ "    --b      ---background        \\t Bei angabe wird das Programm im Hintergrund ausgeführt " + (conf.getRunInBackgroundByDefault() ? "(default)" : "") +  "\\n \\" + "\n"
							+ "    --s      ---stop              \\t Stoppt alle noch laufenden Instanazen \\n \\" + "\n";
				if (uninstaller) batchFileLink 
						+= 	"    --u      ---uninstall         \\t Deinstalliert das Programm (Root-Rechte werden benötigt) \\n \\" + "\n";
				batchFileLink +=
						  "    ---help                       \\t Zeigt diese Hilfe an \"" + "\n"
						+ "\n"
						+ "    exit -1" + "\n"
						+ "}" + "\n"
						+ "\n"
						+ "if [ \"$#\" -gt 0 ]; then" + "\n"
						+ "\n"
						+ "    count=\"1\"" + "\n"
						+ "\n"
						+ "    while [ \"$count\" -le \"$#\" ]; do" + "\n"
						+ "        tmp=\"${!count}\"" + "\n"
						+ "\n"
						+ "        if [ \"$tmp\" = \"--f\" ] || [ \"$tmp\" = \"---foreground\" ]; then foreground=\"true\"" + "\n"
						+ "        elif [ \"$tmp\" = \"--b\" ] || [ \"$tmp\" = \"---background\" ]; then foreground=\"false\"" + "\n"
						+ "        elif [ \"$tmp\" = \"--s\" ] || [ \"$tmp\" = \"---stop\" ]; then stop=\"true\"" + "\n"
						+ "\n"
						+ "        elif [ \"$tmp\" = \"--u\" ] || [ \"$tmp\" = \"---uninstall\" ]; then uninstall=\"true\"" + "\n"
						+ "\n"
						+ "        elif [ \"$tmp\" = \"---help\" ]; then printHelp" + "\n"
						+ "\n"
						+ "        else programOptions=\"\"$programOptions\"\\\"\"\"$tmp\"\"\\\" \"" + "\n"
						+ "        fi" + "\n"
						+ "\n"
						+ "        let \"count++\"" + "\n"
						+ "    done" + "\n"
						+ "fi" + "\n"
						+ "\n"
						+ "# When an programm option was specified the programm will launch in foreground. This rule doesn't get applied, when the user specified that the programm should run in foreground or background" + "\n"
						+ "if [ \"$programOptions\" != \"\" ] && { [ \"$foreground\" = \"falseDefault\" ] || [ \"$foreground\" = \"trueDefault\" ]; }; then foreground=\"true\"; fi" + "\n"
						+ "\n"
						+ "if [ \"$stop\" = \"true\" ]; then " + "pkill -9 -f '" + conf.getApplicationNameShort() + ".jar'" + "\n"
						+ "elif [ \"$uninstall\" = \"true\" ]; then" + "\n"
						+ "    " + conf.getApplicationDir() + "uninstall.sh" + "\n"
						+ "else" + "\n"
						+ "    if [ \"$foreground\" = \"true\" ] || [ \"$foreground\" = \"trueDefault\" ]; then eval \"java" + (conf.getMaxHeapSize() != 0 ? (" -Xmx" + conf.getMaxHeapSize()) + "M" : "") + " -jar \"\"" + pathToLink + "\"\" \"\"$programOptions\"\"\"" + "\n"
						+ "    else ( eval \"java" + (conf.getMaxHeapSize() != 0 ? (" -Xmx" + conf.getMaxHeapSize()) + "M" : "") + " -jar \"\"" + pathToLink + "\"\" \"\"$programOptions\"\" > /dev/null 2> /dev/null\") &" + "\n"
						+ "    fi" + "\n"
						+ "fi" + "\n";
				
				File createLink = new File(destination);
				
				FileWriter fwFile = new FileWriter(createLink);
				PrintWriter pwFile = new PrintWriter(fwFile);
				
				pwFile.print(batchFileLink);
				pwFile.flush();
				pwFile.close();
				
				Process p;
				
				if (uninstaller) p = new ProcessBuilder("bash", "-c", "chmod 0755 " + destination).start();
				else p = new ProcessBuilder("bash", "-c", "chmod +x " + destination).start();
				
				p.waitFor(5000, TimeUnit.SECONDS);
				
			} catch (Exception ex) {
				data.log("w", "Could not create Link to Programm", "registerApplication (create Link)");
			}
		}
		else if (InstallConfig.getOsType() == OSType.WINDOWS) {
			if (destination == null || destination.equals("")) destination = conf.getApplicationDir() + conf.getApplicationNameShort() + ".bat";
			
			try {
				
				String batchFileLink =
						"@echo off\n"
						+ "\n"
						+ ":: using local variables inside an loop\n"
						+ "SETLOCAL ENABLEDELAYEDEXPANSION\n"
						+ "\n"
						+ "SET foreground=\"falseDefault\"\n"
						+ "SET stop=\"false\"\n"
						+ "SET uninstall=\"false\"\n"
						+ "SET programOption=\n"
						+ "\n"
						+ ":: determine the number of given arguments and fill the array\n"
						+ "SET argCount=0\n"
						+ "FOR %%x IN (%*) DO (\n"
						+ "    SET /A argCount+=1\n"
						+ ")\n"
						+ "\n"
						+ ":: cmd only supports 9 parameters :)\n"
						+ "SET pa[0]=%1\n"
						+ "SET pa[1]=%2\n"
						+ "SET pa[2]=%3\n"
						+ "SET pa[3]=%4\n"
						+ "SET pa[4]=%5\n"
						+ "SET pa[5]=%6\n"
						+ "SET pa[6]=%7\n"
						+ "SET pa[7]=%8\n"
						+ "SET pa[8]=%9\n"
						+ "\n"
						+ ":: we are using an endless loop, which counts up to the number of parmeter\n"
						+ ":: here we could also use an normal for loop, but wouldn't be able to get a value from a parameter when\n"
						+ ":: extending the launch script in the future\n"
						+ "SET i=0\n"
						+ ":: at which position we should be -> pass value\n"
						+ "SET iS=0"
						+"\n"
						+ "SET /A argCountDiffOne= %argCount% - 1\n"
						+ "\n"
						+ "FOR /L %%c IN (0, 1, %argCountDiffOne%) DO (\n"
						+ "\n"
						+ "    IF !i! == !iS! (\n"
						+ "\n"
						+ "        :: indicates, if an passed argument should be added to the programm params\n"
						+ "        set add=1\n"
						+ "\n"
						+ "        IF !pa[%%c]! == --f                 ( SET foreground=\"true\" & set add=0 )\n"
						+ "        IF !pa[%%c]! == ---foreground       ( SET foreground=\"true\" & set add=0 )\n"
						+ "\n"
						+ "        IF !pa[%%c]! == --b                 ( SET background=\"true\" & set add=0 )\n"
						+ "        IF !pa[%%c]! == ---background       ( SET background=\"true\" & set add=0 )\n"
						+ "\n"
						+ "        IF !pa[%%c]! == --s                 ( SET stop=\"true\" & set add=0 )\n"
						+ "        IF !pa[%%c]! == ---stop             ( SET stop=\"true\" & set add=0 )\n"
						+ "\n";
				if (uninstaller) batchFileLink
					   += "        IF !pa[%%c]! == --u                 ( SET uninstall=\"true\" & set add=0 )\n"
						+ "        IF !pa[%%c]! == ---uninstall        ( SET uninstall=\"true\" & set add=0 )\n";
				batchFileLink +=
						  "\n"
						+ "        IF !pa[%%c]! == ---help             (GOTO :printHelp)\n"
						+ "\n"
						+ "        IF !add! == 1                       ( SET programOption=!programOption! !pa[%%c]! )\n"
						+ "    )\n"
						+ "\n"
						+ "    SET /A i+=1\n"
						+ "    SET /A iS+=1\n"
						+ ")  \n"
						+ "\n"
						+ ":: main programm logic\n"
						+ "SET exitScript=0\n"
						+ "IF defined programOption if %foreground% == \"falseDefault\"      SET foreground=\"true\"\n"
						+ "IF %foreground% == \"trueDefault\"                                SET foreground=\"true\"\n"
						+ "\n"
						+ "IF %stop% == \"true\" (\n"
						+ "    wmic PROCESS Where \"name Like '%%java%%' AND CommandLine like '%%" + conf.getApplicationNameShort() + "%%'\" Call Terminate\n"
						+ "    SET exitScript=1\n"
						+ ")\n"
						+ "\n"
						+ "IF %uninstall% == \"true\" (\n"
						+ "    CALL " + conf.getApplicationDir().replace("/", "\\") + "uninstall.bat" + "\n"
						+ "    SET exitScript=1\n"
						+ ")\n"
						+ "\n"
						+ "IF %exitScript% == 1 exit /b 0\n"
						+ "\n"
						+ "IF %foreground% == \"true\" (\n"
						+ "    CALL java" + (conf.getMaxHeapSize() != 0 ? (" -Xmx" + conf.getMaxHeapSize()) + "M" : "") + " -jar \"" + pathToLink + "\" %programOption%  \n"
						+ ") ELSE (  \n"
						+ "    CALL START /MIN CMD /C CALL java" + (conf.getMaxHeapSize() != 0 ? (" -Xmx" + conf.getMaxHeapSize()) + "M" : "") + " -jar \"" + pathToLink + "\" %programOption% > NUL  \n"
						+ ")  \n"
						+ "\n"
						+ ":: don't execute printHelp\n"
						+ "GOTO :EOF\n"
						+ "\n"
						+ "\n"
						+ ":printHelp\n"
						+ "ECHO Syntax: RPdb [options] [Programmparameter]\n"
						+ "ECHO.  \n"
						+ "ECHO Optionen:  \n"
						+ "ECHO     --f       ---foreground       Bei angabe wird das Program im Vordergrund ausgeführt  \n"
						+ "ECHO     --b       ---background       Bei angabe wird das Programm im Hintergrund ausgeführt (default)  \n"
						+ "ECHO     --s       ---stop             Stoppt alle noch laufenden Instanzen  \n";
				if (uninstaller) batchFileLink
					   += "ECHO     --u       ---uninstall        Deinstalliert das Prgoramm (Root-Rechte werden benötigt)  \n";
				batchFileLink += 
						  "ECHO     ---help                       Zeigt diese Hilfe an  \n"
						+ "EXIT /b -1  \n";
				
				File createLink = new File(destination);
				
				FileWriter fwFile = new FileWriter(createLink);
				PrintWriter pwFile = new PrintWriter(fwFile);
				
				pwFile.print(batchFileLink);
				pwFile.flush();
				pwFile.close();
				
			} catch (Exception ex) {
				data.log("w", "Could not create Link to Programm", "registerApplication (create Link)");
			}
		}
	}
	
	/**
	 * [Linux] Creates a systemd unit file
	 */
	public void createUnitFile() {
		
		// nothing to do (maybe create a windows service file when needed)
		if (InstallConfig.getOsType() != OSType.LINUX) return;
		
		try {
			
			// check if systemd is present on the machine
			String testCommand = "if [ -d /run/systemd/system/ ]; then echo yes; else echo no; fi";
			Process p = new ProcessBuilder("bash", "-c", testCommand).start();
			p.waitFor();
			BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String output = buf.readLine();
			if (!output.equals("yes")) { data.log("d", "systemd was nout found on the machine -> don't create a service unit", "registerApplication (create Unit File)"); return; }

			// create the unit file
			String 									s  = "[Unit]\n";
													s += "Description=" + (conf.unitDescription == null ? conf.getApplicationNameShort() : conf.unitDescription) + "\n";
			if (conf.unitAfter != null) 			s += "After=" + conf.unitAfter + "\n";
			if (conf.unitStartLimitBurst != null)	s += "StartLimitBurst=" + conf.unitStartLimitBurst + "\n";
			if (conf.unitStartLimitInterval != null)s += "StartLimitIntervalSec=" + conf.unitStartLimitInterval + "\n";

													s += "\n[Install]\n";
													s += "WantedBy=" + (conf.installWantedBy == null ? "multi-user.target" : conf.installWantedBy) + "\n";
			if (conf.installAliasName != null)		s += "Alias=" + conf.installAliasName + "\n";
			
													s += "\n[Service]\n";
			if (conf.serviceExecStartPre != null)   s += replacePaths(conf.serviceExecStartPre.stream().collect(Collectors.joining("\nExecStartPre=", "ExecStartPre=", ""))) + "\n";
			if (conf.serviceExecStartPost != null)  s += replacePaths(conf.serviceExecStartPost.stream().collect(Collectors.joining("\nExecStartPost=", "ExecStartPost=", ""))) + "\n";
			if (conf.serviceWorkingDir != null)		s += "WorkingDirectory=" + replacePaths(conf.serviceWorkingDir) + "\n";
			if (conf.serviceUser != null)			s += "User=" + conf.serviceUser + "\n";
			if (conf.serviceGroup != null)			s += "Group=" + conf.serviceGroup + "\n";
			if (conf.serviceEnvironment != null)	s += conf.serviceEnvironment.stream().collect(Collectors.joining("\nEnvironment=", "Environment=", "")) + "\n";
			if (conf.serviceExecStart != null)		s += replacePaths(conf.serviceExecStart.stream().collect(Collectors.joining("\nExecStart=", "ExecStart=", ""))) + "\n";
			else 									s += "ExecStart=" + replacePaths("#~LaunchScript~# ---background") + "\n";
			if (conf.serviceExecStop != null)		s += "ExecStop=" + replacePaths(conf.serviceExecStop) + "\n";
			if (conf.serviceTimeout != null)		s += "TimeoutSec=" + conf.serviceTimeout + "\n";
 			if (conf.serviceRestart != null)		s += "Restart=" + conf.serviceRestart + "\n";
 			if (conf.serviceRestartSec != null)		s += "RestartSec=" + conf.serviceRestartSec + "\n";
 			
 			// get all users for systemd configs: getent passwd | grep -v '/usr/sbin/nologin' | grep -v '/bin/false' | awk -F: '($6 != "" && ($3 > 10 || $3 == 0)) {print $6}'
			// for Linux only a installation as root is supported -> no user systemd entry
 			String destination = "/etc/systemd/system/" + conf.getApplicationNameShort() + ".service";
			File createLink = new File(destination);
			
			FileWriter fwFile = new FileWriter(createLink);
			PrintWriter pwFile = new PrintWriter(fwFile);
			
			pwFile.print(s);
			pwFile.flush();
			pwFile.close();
			
			// reload systemd
			p = new ProcessBuilder("bash", "-c", "systemctl daemon-reload && systemctl start \"" + conf.getApplicationNameShort() + ".service" + "\"").start();
			p.waitFor(5000, TimeUnit.SECONDS);
			
			if (conf.startAtBoot) {
				p = new ProcessBuilder("bash", "-c", "systemctl enable \"" + conf.getApplicationNameShort() + ".service"+ "\"").start();
				p.waitFor(5000, TimeUnit.SECONDS);
			}
			
		} catch (Exception ex) {
			data.log("w", "Could not create a systemd unit file", "registerApplication (create Unit File)");
		}
	}
	
	private String replacePaths(String replaceString, boolean addQuotes) {
		if (replaceString == null) return null;
		
		String quotes = addQuotes ? "\"" : "";
		return replaceString
				.replace("#~LaunchScript~#", quotes + "/usr/bin/" + conf.getApplicationNameShort() + quotes )
				.replace("#~AppPath~#", quotes + conf.getApplicationDir() + quotes )
				.replace("#~ConfigPath~#", quotes + conf.getConfigDir() + quotes);
	}
	private String replacePaths(String replaceString) {
		return replacePaths(replaceString, false);
	}
	
	/**
	 * Überprüft, ob ein Nutzer unter Linux oder Windows root Rechte hat
	 * @return Ob de Nutzer Root Rechte hat
	 */
	private boolean checkRoot() {
		
		if (InstallConfig.getOsType() == OSType.WINDOWS) {
			
			// als erstes wird überprüft, ob der Nutzer sich überhaupt in einer Admin-Grupper befindet (zweiter Abschnitt wirft eine unvermeidbare Warnung)
			// kann nur vermiden werden, wenn der Schlüssel HKEY_LOCAL_MACHINE\SOFTWARE\JavaSoft\Prefs in der Registry eingetragen wurde
			boolean isAdminGroup = false;
		    String groups[] = (new com.sun.security.auth.module.NTSystem()).getGroupIDs();
		    for (String group : groups) {
		        if (group.equals("S-1-5-32-544")) isAdminGroup = true;
		    }
		    
		    if (!isAdminGroup) return false;
		    
			// Um herauszufinden, ob ein Benutzer das Programm mit Administratorprivelegien gestartet hat, wird versucht, System-Preferences zu schreiben.
			// Falls hierbei ein Fehler auftritt, kann davon ausgegeangen werden, dass der entsprechende Nutzer keine Administratorprivelegien hat / das Programm nicht mit diesen gestartet hat
			Preferences preferences = systemRoot();
			synchronized (System.err) {
				setErr(new PrintStream(new OutputStream() {
					@Override
					public void write(int b) { }
				}));

				try {
					preferences.put("foo", "bar");	// Windows
					preferences.remove("foo");
					preferences.flush(); 			// Linux
					return true;
				} catch (Exception ex) {
					return false;
				} finally {
					setErr(System.err);
				}
			}
			
		}
		
		try {
			Process p = new ProcessBuilder("bash", "-c", "id -u").start();
			p.waitFor(5, TimeUnit.SECONDS);
			
			BufferedReader buf = new BufferedReader(new InputStreamReader(p.getInputStream()));
			String a = "";
			String line = null;
			if ((line = buf.readLine()) != null) a = line;
			
			if (a.equals("0")) return true;

			return false;
			
		} catch (Exception ex) {
			data.log("w", ex, "checkRoot");
			return false;
		}
	}
	
	/**
	 * Lädt eine Datei von einem Webserver herunter (mit Basic-Auth support -> config)
	 * @param url		 	die URL
	 * @param addVersion 	ob das Betriebsystem und die Architektur der URL angehängt werden soll -> windows_x64
	 * @param end		 	das Dateiende (die URL wird in diesem Fall ohne Dateiendung angegeben)
	 * @return 				Der Pfad der heruntergeladenen Datei (bei Fehler: null + data.error)
	 */
	private String downloadFile (String url2, boolean addVersion, String end) {

		String serverURL = conf.downloadURL;
		
		if (addVersion) serverURL += "_" + this.getVersionOfProgramm() + end;
		
		try {
			URL url = new URL(serverURL);
	        HttpURLConnection con = (HttpURLConnection) url.openConnection();
	        if (conf.authUsername != null && conf.authPassword != null) {
				String auth = new String(conf.authUsername) + ":" + new String(conf.authPassword);
		        byte[] authEncBytes = Base64.getEncoder().encode(auth.getBytes());
		        String authHeaderValue = "Basic " + new String(authEncBytes);
		        con.setRequestProperty("Authorization", authHeaderValue);
		        con.setRequestProperty("X-Requested-With", "XMLHttpRequest");
	        }
			
			try {						
				File download = File.createTempFile("Download-Installation", ".jar");	
				
				// für eine Statusanzeige wird zunächst die Dateigröße der Datei ermittelt (wird in Bytes ausgegeben)
				double lenght = con.getContentLength();
				if (lenght < 100 * 1024) { throw new Exception ("Probably not a file (lenght to short)"); }
				// diese wird nun in Megabyte angegeben, sowie auf 2 Stellen nach dem Komma (Es wird die bereits heruntergeladene Dateigröße im "Binärformat" angegeben
				lenght = Math.round(lenght / 1048576 * 100) / 100.0;
				
				// es muss nun parallel die bereits heruntergeladene Dateigröße ermittelt werden
				ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
				double lenghtTmp = lenght;
				Future future = scheduler.scheduleWithFixedDelay(() -> {
					double actualLenghtOfFile = download.length();
					actualLenghtOfFile = Math.round(actualLenghtOfFile / 1048576 * 100) / 100.0;
					
					double percent = Math.round(actualLenghtOfFile / lenghtTmp * 10000) / 100.0;
					// add leading zeros
					DecimalFormat f = new DecimalFormat("0.00");
					System.out.print("\rDownloading file: " + f.format(percent) + "% (" + f.format(actualLenghtOfFile) + " MB / " + lenghtTmp + " MB)");
					;
				}, 200, 450, TimeUnit.MILLISECONDS);
				
				// open the stream and download
				ReadableByteChannel rbc = Channels.newChannel(con.getInputStream());
				FileOutputStream fos = new FileOutputStream(download);
				fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
				fos.close();
				future.cancel(true);
				
				return download.getAbsolutePath();
				
			} catch (Exception ex) {
				System.out.println("fehler");
				System.out.println("\nA error occured while downloading the file.\nPlease check your internet connection and try again later (URL: " + serverURL + ")");
				error = -20;
				data.log("e", ex, "downloadFile");
			}
		} catch (Exception ex) {
			System.out.println("The determined URL was not found" + "(URL: " + serverURL + ")");
			error = -21;
		}
		
		return null;

	}
	
	/**
	 * Gibt die zu herunterladende Version des Programms aus
	 * @return den entsprechenden Dateinamen (z.B. windows_x64, linux_arm64)
	 */
	protected String getVersionOfProgramm() {
		
		String rtc  = "";
		
		// Verwendetes Betriebssystem ermitteln //
		if (InstallConfig.getOsType() == OSType.WINDOWS) rtc += "windows";
		else if (InstallConfig.getOsType() == OSType.LINUX) rtc += "linux";
		else if (InstallConfig.getOsType() == OSType.MACOS) rtc += "mac";	
		else {
			System.out.println("Bettriebssystem konnte nicht ermittelt werden: " + System.getProperty("os.name"));
			System.exit(-1);
		}
		
		rtc += "_";
		
		// Architektur der CPU ermitteln //
		String aarch = System.getProperty("os.arch").toLowerCase();
		if (aarch.contains("amd64")) rtc += "x64";
		else if (aarch.equals("x86")) rtc += "x86";
		else if (aarch.equals("arm64") || aarch.equals("aarch64")) rtc += "arm64";
		else if (aarch.equals("arm")) rtc += "arm32";
		//fals nichts zutrifft, aber Prozessor mit 64 endet, wird amd64 erwartet
		else if (aarch.endsWith("64")) rtc += "x64";
		else {
			System.out.println("CPU architecture could not been determined: " + aarch);
			System.exit(-2);
		}
		
		return rtc;
	}
	
	private void installFonts() {
		
		if (conf.getFontsToInstall().isEmpty()) return;
		
		if (InstallConfig.getOsType() == OSType.WINDOWS) {
			
			if (this.checkRoot()) {
				
				String path = System.getenv("WINDIR") + "\\Fonts\\";
				String batchFile = "";
				for (Map.Entry<String, String> entry: conf.getFontsToInstall().entrySet()) {
					conf.getResource(entry.getValue(), path + entry.getKey() + ".ttf", false);
					batchFile += "reg add \"HKLM\\SOFTWARE\\Microsoft\\Windows NT\\CurrentVersion\\Fonts\" /v \"" + entry.getKey() + " (TrueType)\" /t REG_SZ /d \"" + path + entry.getKey() + ".ttf\" /f \n";
				}
				try {
					// es wird die Batch-Datei erstellt, und anschließend ausgeführt
					File batchMakeRegeditEntry = File.createTempFile("installApplication", ".bat");
					
					FileWriter fwFile = new FileWriter(batchMakeRegeditEntry);
					PrintWriter pwFile = new PrintWriter(fwFile);
					
					pwFile.print(batchFile);
					pwFile.flush();
					pwFile.close();

					Process p = new ProcessBuilder("cmd.exe", "/C", batchMakeRegeditEntry.getAbsolutePath()).start();
					if (!p.waitFor(10, TimeUnit.SECONDS)) data.log("w", "Batch File which adds Registry Keys for uninstallation not fully executed (timeout)", "registerApplication");
				} catch (Exception ex) { data.log("e", ex, "installFonts"); }
				
			} else {
				data.log("w", "Can't install fonts without admin privilegies in Windows -> skipping. You should mind a reinstall with Admin-Privelegis", "installFonts");
			}
			
		} else if (InstallConfig.getOsType() == OSType.LINUX) {
			
			String path = "";
			if (this.checkRoot()) path = "/usr/share/fonts/truetype/";	
			else path = System.getProperty("user.home") + "/.local/share/fonts/";
			
			// Verzeichnis erstellen, falls noch nicht vorhanden
			new File(path).mkdirs();
			
			final String path_ = path;
			conf.getFontsToInstall().forEach( (name, pathInJar) -> {
				conf.getResource(pathInJar, path_ + name + ".ttf");
			});
			
			try {
				// Berechtigungen korrekt setzen -> Ordner: 0755 | Dateien: 0644
				Process p;
				
				p = new ProcessBuilder("bash", "-c", "chmod -R 0644" + path + "*").start();
				p.waitFor(5, TimeUnit.SECONDS);
				
				p = new ProcessBuilder("bash", "-c", "find " + path + " -type d -exec chmod 0755 {} +").start();
				p.waitFor(5, TimeUnit.SECONDS);
				
				p = new ProcessBuilder("bash", "-c", "fc-cache -f -v").start();
				p.waitFor(5, TimeUnit.SECONDS);
				
			} catch (Exception ex) { }
		}
	}	
	
	/**
	 * Returns if the installation was successful (0 = successful, {@literal <}0 = error)
	 * 
	 * @return	the error code
	 */
	public int getResponseCode() { return error; }
	
}

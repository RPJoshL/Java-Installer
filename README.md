# Purpose

This project provides a simple installation tool for installing your java application under the operation systems *windows* and *linux*.  

If you want to use a simple installer instead of the installation method the operating systems ships with (like *msi* or *dpkg packages*) feel free to use this installer.  
This installer can be used for a **single jar file** with a few dependencies.

# Features

### Windows and Linux

* quiet installation without opening a command prompt
* installation of **fonts** from the source folder (*.tft*)
* the executable to install can be downloaded from a webserver.  
Basic auth for downloading the executable is supported
* install the program *portable* in a single folder
* creating a **desktop** and a **start menu** entry with a custom icon
* a **launch script** for opening the application will be provided

### Windows

* besides the removal via the launch script an **uninstall** entry in the *contol center* will also be created
* install the programm only for the current **user** -> no need of administrator rights

### Linux

* creation of a *systemd* unit file to run the program as a **service**

# Getting started

The usage of the library is very simple. See the below code snippet for a short example.

```
InstallConfig conf = new InstallConfig (
    "myCompany", 
    "2.0.0", 
    "MyApplicationName", 
    "My long application name")
;
// now you can specify the various options via the InstallConfig
conf.setDownloadURLForProgramm(URL, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
...   // see the javadoc for more options

// after configuring you can install the application
Installer installer = new Installer(conf);
installer.installProgramm(args);

// whether the installation was successful (0) or erroneous (<0)
System.out.println(installer.getResponseCode());
```
___

For a real life example you can take a look at the installer of [RPdb](https://git.rpjosh.tk/RPJosh/RPdb/src/branch/master/Program/Java/tk.rpjosh.rpdb.installer).

# License
This project is licensed under the GPLv3. Please see the [LICENSE](LICENSE) file for an full license.

# Need help?
You can check out the 📖️ Javadocs for more informations.

If that didn't help you feel free to create an issue or open a pull request 📣️
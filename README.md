# Purpose

This project provides a simple installation tool for installing your java application for the operating systems *windows* and *linux*.  

If you want to use a simple installer instead of the installation method the operating systems ships with (like *msi* or *dpkg packages*) feel free to use this installer.  
It can be used for a **single jar file** with a few dependencies.

# Features

### Windows and Linux

* quiet installation without opening a command prompt
* installation of **fonts** from the source folder (*.tft*)
* dynamic download of the jar file from a webserver.  
Basic auth for downloading the executable is supported
* install the program *portable* in a single folder
* creating a **desktop** and a **start menu** entry with a custom icon
* a **launch script** for opening the application will be provided
* put your application into the autostart folder of the operating system *(for GUI applications)*

### Windows

* besides the removal via the launch script an **uninstall** entry in the *contol center* will be created
* installation of the programm only for the current **user** → no need of administrator rights

### Linux

* creation of a *systemd* unit file to run the program as a **service**

# Getting started

## How to get

You can build the library by yourself or use the provided version in the [Maven Central Repository](https://central.sonatype.com/artifact/de.rpjosh/installer).

## Usage

The usage of the library is very simple. See the below code snippet for a short example.

```java
InstallConfig conf = new InstallConfig (
    "myCompany", 
    "2.0.0", 
    "MyApplicationName", 
    "My long application name")
;
// You can specify the various options via the InstallConfig object
conf.setDownloadURLForProgramm(URL, BASIC_AUTH_USER, BASIC_AUTH_PASSWORD);
...   // see the javadoc for more options

// After configuring you can install the application
Installer installer = new Installer(conf);
installer.installProgramm(args);

// Whether the installation was successful (0) or erroneous (>0)
System.out.println(installer.getResponseCode());
```

# License
This project is licensed under the GPLv3. Please see the [LICENSE](LICENSE) file for a full license.

# Need help?
You can check out the 📖️ Javadocs for more information.

If that didn't help you feel free to create an issue or open a pull request 📣️
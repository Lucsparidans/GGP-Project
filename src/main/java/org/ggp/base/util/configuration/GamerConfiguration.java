/**
 *  GamerConfiguration handles machine/OS-specific details, so that these
 * details don't end up hard-coded into the gamer itself. Currently, these
 * details include:
 *
 *  > Locations of the "java" and "javac" binaries, independent of OS.
 *
 *  > Amounts of RAM to allocate to the gamer when running in Proxy mode,
 *    based on the name of the system it's running on.
 *
 *  Machine-specific profiles are stored in an optional "gamerProfiles" file,
 * which has the following format:
 *
 *      (system name) <tab> (allocated RAM in MB) <tab> (proper name)
 *
 *  When the program begins, GamerConfiguration will automatically determine
 * which profile is applicable, and which operating system is running. From
 * then on, it can be called upon to provide information. If you want to add
 * a default profile, add an entry with (system name) equal to "*", and when
 * none of the earlier profiles match, that profile will be used.
 *
 * @author Sam Schreiber
 */

package org.ggp.base.util.configuration;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.net.InetAddress;

public class GamerConfiguration {

	/**
	 * Path of the folder containing the local version of the GGP-Base game repository
	 */
	public static final String defaultLocalGameRepositoryFolderPath;
	public static final String defaultGGPBaseRepo = "GGPBase-GameRepo-03022016";
	public static final String defaultStanfordRepo = "Stanford-GameRepo-01062018";
	public static final String gamersSettingsFolderPath;

	/**
	 * Path of the local folder containing .kif game description files collected manually by other repositories
	 */
	public static final String defaultLocalFolderGameRepositoryFolderPath;

    private static String strSystemOS;
    private static String strProfileName;
    private static int nMemoryForGamer;     // in MB
    private static int nOperatingSystem;

    private static final int OS_WINDOWS = 1;
    private static final int OS_MACOS = 2;
    private static final int OS_LINUX = 3;

    public static void showConfiguration() {
        String osType = "Unknown";
        if(runningOnLinux()) osType = "Linux";
        if(runningOnMacOS()) osType = "MacOS";
        if(runningOnWindows()) osType = "Windows";
        System.out.println(String.format("Configured according to the %s Profile, running on %s which is a variety of %s, allocating %d MB of memory to the gaming process.", strProfileName, strSystemOS, osType, nMemoryForGamer));
    }

    private static String getComputerName() {
        try {
            return InetAddress.getLocalHost().getHostName().toLowerCase();
        } catch (Exception e) {
            return null;
        }
    }

    static {
        strProfileName = getComputerName();

        boolean foundProfile = false;
        try {
            String line;
            BufferedReader in = new BufferedReader(new FileReader("src/org/ggp/base/util/configuration/gamerProfiles"));
            while((line = in.readLine()) != null) {
                if(line.length() == 0) continue;
                if(line.charAt(0) == '#') continue;
                String[] splitLine = line.split("\\s+");
                if(splitLine[0].equals(strProfileName)) {
                    nMemoryForGamer = Integer.parseInt(splitLine[1]);
                    strProfileName = splitLine[2];
                    foundProfile = true;
                    break;
                } else if(splitLine[0].equals("*")) {
                    nMemoryForGamer = Integer.parseInt(splitLine[1]);
                    strProfileName = splitLine[2];
                    foundProfile = true;
                    break;
                }
            }
            in.close();
        } catch (FileNotFoundException fe) {
            ;
        } catch (Exception e) {
            e.printStackTrace();
        }

        if(!foundProfile) {
            nMemoryForGamer = 1000;
            strProfileName = "Default";
        }

        strSystemOS = System.getProperty("os.name");
        if(strSystemOS.contains("Linux")) {
            nOperatingSystem = OS_LINUX;
        } else if(strSystemOS.contains("Mac OS")) {
            nOperatingSystem = OS_MACOS;
        } else if(strSystemOS.contains("Windows")) {
            nOperatingSystem = OS_WINDOWS;
        } else {
            ;
        }

        if(runningOnWindows()){
			defaultLocalGameRepositoryFolderPath = "C:/Users/lucsp/git/GGP-Project";
			gamersSettingsFolderPath = "C:/Users/lucsp/git/GGP-Project/GamersSettings";
			defaultLocalFolderGameRepositoryFolderPath = "C:/Users/lucsp/git/GGP-Project/GDLFolder";

			// To run the gamers properties from the same folder
			//defaultLocalGameRepositoryFolderPath = ".";
			//gamersSettingsFolderPath = ".";
			//defaultLocalFolderGameRepositoryFolderPath = ".";
			// To run Kiosk on laptop
			//defaultLocalGameRepositoryFolderPath = "C:/Users/Chiara/Documents/PHD/GGP DEMO/GGPBase-GameRepo-03022016";
			//gamersSettingsFolderPath = "C:/Users/Chiara/Documents/PHD/GGP DEMO/GamersSettings";
			// To run Kiosk on work laptop
			//defaultLocalGameRepositoryFolderPath = "C:/Users/c.sironi/Documents/GGPBase/GGPBase-GameRepo-03022016";
			//gamersSettingsFolderPath = "C:/Users/c.sironi/Documents/GGPBase/GamersSettings";
		}else if(runningOnLinux()){

			// GoGeneral + Go4Nature
//			defaultLocalGameRepositoryFolderPath = "/home/csironi/GAMEREPOS"; // Folder with GDL game descriptions
//			gamersSettingsFolderPath = "/home/csironi/GamersSettings"; // Folder with Agent's properties
//			defaultLocalFolderGameRepositoryFolderPath = "/home/csironi/GDLFolder"; // Own games folder

			// Aachen
			defaultLocalGameRepositoryFolderPath = "/home/iy353734/GAMEREPOS";
			gamersSettingsFolderPath = "/home/iy353734/GamersSettings";
			defaultLocalFolderGameRepositoryFolderPath = "/home/iy353734/GDLFolder";



			//defaultLocalGameRepositoryFolderPath = ".";
			//gamersSettingsFolderPath = ".";
			//defaultLocalFolderGameRepositoryFolderPath = ".";
			//defaultLocalGameRepositoryFolderPath = "/data/home/eex264/chiaraxps/GGPBase-GameRepo-03022016";
			//gamersSettingsFolderPath = "/data/home/eex264/chiaraxps/GamersSettings";



		}else{
			//defaultLocalGameRepositoryFolderPath = null;
			//gamersSettingsFolderPath = null;
			//defaultLocalFolderGameRepositoryFolderPath = null;

			defaultLocalGameRepositoryFolderPath = ".";
			gamersSettingsFolderPath = ".";
			defaultLocalFolderGameRepositoryFolderPath = ".";
		}
    }

    // OS-neutral accessors

    public static String getCommandForJava() {
        if(nOperatingSystem == OS_MACOS) {
            return "/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Commands/java";
        } else {
            return "java";
        }
    }

    public static String getCommandForJavac() {
        if(nOperatingSystem == OS_MACOS) {
            return "/System/Library/Frameworks/JavaVM.framework/Versions/1.6.0/Commands/javac";
        } else {
            return "javac";
        }
    }

    // Accessors

    public static boolean runningOnLinux () {
        return nOperatingSystem == OS_LINUX;
    }

    public static boolean runningOnMacOS () {
        return nOperatingSystem == OS_MACOS;
    }

    public static boolean runningOnWindows () {
        return nOperatingSystem == OS_WINDOWS;
    }

    public static String getOperatingSystemName() {
        return strSystemOS;
    }

    public static String getProfileName() {
        return strProfileName;
    }

    public static int getMemoryForGamer() {
        return nMemoryForGamer;
    }

    public static void main(String args[]) {
        System.out.println("Computer name: " + getComputerName());
        GamerConfiguration.showConfiguration();
    }
}
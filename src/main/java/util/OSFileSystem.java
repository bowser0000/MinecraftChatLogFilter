package util;

import main.MainWindow;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class OSFileSystem {
	private final MainWindow mainWindow;

	public OSFileSystem(MainWindow mainWindow) {
		this.mainWindow = mainWindow;
	}

	public ArrayList<File> lookForMinecraftLogFolders() {
		// get roots of file system structure of your OS
		mainWindow.addStatus("INFO: Analyzing file system structure ...");
		File[] rootFileSystems = File.listRoots();
		if (rootFileSystems.length <= 0) {
			mainWindow.addStatus("ERROR: No file systems found!");
			return null;
		}
		mainWindow.addStatus("INFO: Found " + Arrays.toString(rootFileSystems));

		// adjust Minecraft file path to operating system and found drives
		String homePath = System.getProperty("user.home");
		File currentMinecraftFolder;
		switch (OSName.getOperatingSystemType()) {
		case Windows:
			mainWindow.addStatus("INFO: Found Windows operating system");
			for (File drive : rootFileSystems)
				mainWindow.getAdditionalFolderPaths()
						.add(drive + homePath.substring(3) + "\\AppData\\Roaming\\.minecraft");
			break;
		case MacOS:
			mainWindow.addStatus("INFO: Found MacOS operating system");
			mainWindow.getAdditionalFolderPaths().add(homePath + "/Library/Application Support/minecraft");
			break;
		case Linux:
			mainWindow.addStatus("INFO: Found Linux operating system");
			mainWindow.getAdditionalFolderPaths().add(homePath + "/.minecraft");
			break;
		case Other:
			mainWindow.addStatus("ERROR: Unknown operating system!");
			break;
		}
		ArrayList<File> minecraftFolders = new ArrayList<>();
		for (String path : mainWindow.getAdditionalFolderPaths()) {
			currentMinecraftFolder = new File(path);
			if (currentMinecraftFolder.exists())
				minecraftFolders.add(currentMinecraftFolder);
		}
		if (minecraftFolders.size() <= 0) {
			mainWindow.addStatus("ERROR: No minecraft folders found!");
		}
		for (File minecraftFolder : minecraftFolders)
			mainWindow.addStatus("INFO: Found " + minecraftFolder.getAbsolutePath());

		// get log folders
		ArrayList<File> minecraftLogFolders = new ArrayList<>();
		for (File minecraftFolder : minecraftFolders) {
			for (File folder : minecraftFolder.listFiles()) {
				if (folder.isDirectory()) {
					if (folder.getName().equalsIgnoreCase("logs")) {
						minecraftLogFolders.add(folder);
					} else {
						for (File subFolder : folder.listFiles()) {
							if (subFolder.getName().equalsIgnoreCase("logs"))
								minecraftLogFolders.add(subFolder);
						}
					}
				}
			}
		}
		return minecraftLogFolders;
	}
}

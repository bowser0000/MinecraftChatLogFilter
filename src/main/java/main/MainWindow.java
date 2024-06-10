package main;

import util.LogRecords;
import util.MCLogFile;
import util.MCLogLine;
import util.OSFileSystem;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.*;

/**
 *
 * @author doej1367
 */
public class MainWindow extends JFrame {
	private static final long serialVersionUID = 1L;
	private static MainWindow mainWindow;
	private FolderWindow folderWindow;

    private final JTextArea inputTextField;
	private final JTextArea outputTextField;
	private final JScrollPane scrollPaneBottom;
	private final JTextArea statusTextField;

    private final TreeSet<String> additionalFolderPaths = new TreeSet<>();

    private int tmpStauslength = 0;
    private final JMenuItem addFoldersButtonMenuItem;
	private final JCheckBoxMenuItem onlyChatCheckBoxMenu;
    private final Button defaultButton;
    private final JCheckBoxMenuItem stripColorCodesCheckBoxMenu;

	/**
	 * Create the frame.
	 */
	public MainWindow() {
		setFont(new Font("Consolas", Font.PLAIN, 14));
		// create window
		setTitle("Minecraft Chat Log Filter");
		setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
		setBounds(100, 100, 800, 540);
		setMinimumSize(getSize());
		setLocationRelativeTo(null);
        JPanel contentPane = new JPanel();
		contentPane.setBorder(new EmptyBorder(5, 5, 5, 5));
		contentPane.setLayout(new BorderLayout(0, 5));
		setContentPane(contentPane);

		// add buttons
        JPanel panel_north = new JPanel();
		contentPane.add(panel_north, BorderLayout.NORTH);
		panel_north.setLayout(new BoxLayout(panel_north, BoxLayout.X_AXIS));

        Component horizontalGlue_2 = Box.createHorizontalGlue();
		panel_north.add(horizontalGlue_2);

		defaultButton = new Button("Start analyzing currently known .minecraft folders");
		defaultButton.addActionListener(e -> startAnalysis());
		defaultButton.setForeground(Color.BLUE);
		defaultButton.setFont(new Font("Consolas", Font.PLAIN, 14));
		defaultButton.setBackground(new Color(240, 248, 255));
		panel_north.add(defaultButton);

        JMenuBar menuBar = new JMenuBar();
		menuBar.setBorderPainted(false);
		panel_north.add(menuBar);

        JMenu mnOptionsMenu = new JMenu("");
		mnOptionsMenu.setIcon(new ImageIcon(this.getClass().getResource("/icon-more-20.png")));
		mnOptionsMenu.setSize(20, 20);
		mnOptionsMenu.setHorizontalAlignment(SwingConstants.RIGHT);
		mnOptionsMenu.setFont(new Font("Consolas", Font.PLAIN, 14));
		menuBar.add(mnOptionsMenu);

		addFoldersButtonMenuItem = new JMenuItem("Add custom .minecraft folder locations");
		addFoldersButtonMenuItem.setFont(new Font("Consolas", Font.PLAIN, 14));
		addFoldersButtonMenuItem.addActionListener(e -> EventQueue.invokeLater(() -> {
            try {
                if (folderWindow == null)
                    folderWindow = new FolderWindow(mainWindow);
                folderWindow.setVisible(true);
            } catch (Exception ignored) {
            }
        }));
		mnOptionsMenu.add(addFoldersButtonMenuItem);

		stripColorCodesCheckBoxMenu = new JCheckBoxMenuItem("Strip color codes");
		stripColorCodesCheckBoxMenu.setSelected(true);
		stripColorCodesCheckBoxMenu.setFont(new Font("Consolas", Font.PLAIN, 14));
		mnOptionsMenu.add(stripColorCodesCheckBoxMenu);

		onlyChatCheckBoxMenu = new JCheckBoxMenuItem("Only filter chat lines");
		onlyChatCheckBoxMenu.setFont(new Font("Consolas", Font.PLAIN, 14));
		onlyChatCheckBoxMenu.setSelected(true);
		mnOptionsMenu.add(onlyChatCheckBoxMenu);

        Component horizontalGlue_1 = Box.createHorizontalGlue();
		panel_north.add(horizontalGlue_1);

        JPanel panel_south = new JPanel();
		contentPane.add(panel_south, BorderLayout.CENTER);
		panel_south.setLayout(new BorderLayout(0, 0));

		// add a scrollable text field with multiple lines of text for output
		JScrollPane scrollPaneTop = new JScrollPane();
		panel_south.add(scrollPaneTop, BorderLayout.NORTH);
		inputTextField = new JTextArea();
		inputTextField.setLineWrap(true);
		inputTextField.setWrapStyleWord(true);
		inputTextField.setRows(4);
		inputTextField.setFont(new Font("Consolas", Font.PLAIN, 14));
		inputTextField.setText(
				"(You bought Kismet Feather!.*)|(You purchased .*Kismet Feather .*)|(The Catacombs - Floor VII)->(Team Score: [0-9]+ \\(S\\+\\).*)");
		inputTextField.setEditable(true);
		scrollPaneTop.setViewportView(inputTextField);

		// add a scrollable text field with multiple lines of text for output
		JScrollPane scrollPaneMiddle = new JScrollPane();
		panel_south.add(scrollPaneMiddle, BorderLayout.CENTER);
		outputTextField = new JTextArea();
		outputTextField.setFont(new Font("Consolas", Font.PLAIN, 14));
		outputTextField.setText("");
		outputTextField.setEditable(false);
		scrollPaneMiddle.setViewportView(outputTextField);

		// add a scrollable text field with multiple lines of text for debug output
		scrollPaneBottom = new JScrollPane();
		panel_south.add(scrollPaneBottom, BorderLayout.SOUTH);
		statusTextField = new JTextArea();
		statusTextField.setRows(5);
		statusTextField.setFont(new Font("Consolas", Font.PLAIN, 14));
		statusTextField.setText("");
		statusTextField.setEditable(false);
		scrollPaneBottom.setViewportView(statusTextField);
	}

	private void startAnalysis() {
		defaultButton.setEnabled(false);
		addFoldersButtonMenuItem.setEnabled(false);
		Thread t0 = new Thread(() -> mainWindow.analyze(onlyChatCheckBoxMenu.getState(), stripColorCodesCheckBoxMenu.getState()));
		t0.start();
	}

	private synchronized void analyze(boolean onlyChat, boolean stripColorCodes) {
		try {
			OSFileSystem fileSystem = new OSFileSystem(mainWindow);
			ArrayList<File> minecraftLogFolders = fileSystem.lookForMinecraftLogFolders();

			// look for log files
			ArrayList<File> allFiles = new ArrayList<>();
			for (File minecraftLogFolder : minecraftLogFolders) {
				addStatus("INFO: Gathering log files from " + minecraftLogFolder.getAbsolutePath());
				for (File logFile : minecraftLogFolder.listFiles())
					if (logFile.getName().matches("[0-9]{4}-[0-9]{2}-[0-9]{2}-[0-9]+\\.log\\.gz|latest\\.log"))
						allFiles.add(logFile);
			}

			// analyze all found log files
			addStatus("INFO: Loading log files (this might take a minute)");

			int counter = 0;
			int fileCount = allFiles.size();
			MCLogFile minecraftLogFile;
			TreeMap<String, Integer> playerNames = new TreeMap<>();
			String lastLoginName = null;
			String lineFilter;

			String logLineSpecialFilterRegex = inputTextField.getText();

			List<MCLogLine> relevantLogLines = new ArrayList<>();
            LogRecords logRecords = new LogRecords(relevantLogLines, logLineSpecialFilterRegex);

			allFiles.sort((f1, f2) -> {
                long tmp = getLastModifiedTime(f1) - getLastModifiedTime(f2);
                return tmp < 0 ? -1 : tmp > 0 ? 1 : 0;
            });
			for (File logFile : allFiles) {
				if (counter++ % 50 == 0)
					addStatusTemporaryly(
							"INFO: Loading " + fileCount + " files - " + (counter * 100 / fileCount) + "%");
				try {
					minecraftLogFile = new MCLogFile(logFile, getPreviousFileInFolder(counter, allFiles), onlyChat, stripColorCodes);
					if (minecraftLogFile.getPlayerName() != null) {
						lastLoginName = minecraftLogFile.getPlayerName();
						playerNames.put(lastLoginName, playerNames.getOrDefault(lastLoginName, 0) + 1);
					}
					lineFilter = logRecords.getLineFilter();
					relevantLogLines.addAll(minecraftLogFile.filterLines(lineFilter, lastLoginName));
				} catch (IOException ignored) {
				}
			}
			Collections.sort(relevantLogLines);

			Optional<Map.Entry<String, Integer>> optionalName = playerNames.entrySet().stream().max(Comparator.comparingInt(Map.Entry::getValue));
			String name = optionalName.map(Map.Entry::getKey).orElse(null);
			for (MCLogLine l : relevantLogLines) {
				if (l.getPlayerName() == null)
					l.setPlayerName(name);
				else
					break;
			}
			// addStatus("INFO: Found most logins from " + name);

			for (int i = 0; i < relevantLogLines.size(); i++)
				logRecords.add(i);

			// send data to google form
			StringBuilder sb = new StringBuilder();
			for (MCLogLine entry : logRecords) {
				sb.append(entry.getCreationTime());
				sb.append(":");
				sb.append(entry.getText());
				sb.append("\n");
			}
			setOutput(sb.toString());
			addStatus("INFO: Found " + logRecords.size() + " results!");
			defaultButton.setEnabled(true);
			addFoldersButtonMenuItem.setEnabled(true);
		} catch (Exception e) {
			addStatus("ERROR: " + e);
			StringBuilder sb = new StringBuilder();
			for (StackTraceElement elem : e.getStackTrace()) {
				sb.append("        ");
				sb.append(elem.toString());
				sb.append("\n");
			}
			addStatus(sb.toString());
		}
	}

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(() -> {
            try {
                mainWindow = new MainWindow();
                mainWindow.setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
	}

	/**
	 * Adds a new line of text to the outputTextField
	 *
	 * @param s - text to add
	 */
	public void setOutput(String s) {
		outputTextField.setText(s);
	}

	/**
	 * Adds a new line of text to the statusTextField
	 *
	 * @param s - text to add
	 */
	public void addStatus(String s) {
		String oldText = statusTextField.getText();
		statusTextField.setText(oldText.substring(0, oldText.length() - tmpStauslength) + s + "\n");
		tmpStauslength = 0;
		JScrollBar vertical = scrollPaneBottom.getVerticalScrollBar();
		vertical.setValue(vertical.getMaximum());
	}

	public void addStatusTemporaryly(String s) {
		if (tmpStauslength == 0)
			scrollPaneBottom.getVerticalScrollBar().setValue(scrollPaneBottom.getVerticalScrollBar().getMaximum());
		String oldText = statusTextField.getText();
		statusTextField.setText(oldText.substring(0, oldText.length() - tmpStauslength) + s);
		tmpStauslength = s.length();
	}

	public TreeSet<String> getAdditionalFolderPaths() {
		return additionalFolderPaths;
	}

	private long getLastModifiedTime(File file) {
		try {
			return Files.readAttributes(file.toPath(), BasicFileAttributes.class).lastModifiedTime().toMillis();
		} catch (IOException ignored) {
		}
		return 0;
	}

	private File getPreviousFileInFolder(int counter, ArrayList<File> allFiles) {
		File current = allFiles.get(counter - 1);
		File previous;
		for (int i = counter - 1; i > 0; i--) {
			previous = allFiles.get(i - 1);
			if (previous.getParentFile().equals(current.getParentFile()) && previous.getName().endsWith(".gz"))
				return previous;
		}
		return null;
	}
}

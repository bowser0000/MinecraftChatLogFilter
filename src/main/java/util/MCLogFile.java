package util;

import org.mozilla.universalchardet.UniversalDetector;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;

public class MCLogFile {
	private static String tmpPlayerName; // needs to be static. Streams are weird...
	private final boolean stripColorCodes;
	private final long creationTime;
	private Long startingTimeOfFile;
	private final String playerName;
	private final Stream<String> linesStream;

	public MCLogFile(File logFile, File previousLogFile, boolean onlyChat, boolean stripColorCodes)
			throws IOException {
		this.stripColorCodes = stripColorCodes;
		creationTime = previousLogFile != null ? getCreationTime(previousLogFile) : getCreationTime(logFile) - 21600000;
		startingTimeOfFile = null;

		InputStream inputStream = Files.newInputStream(logFile.toPath());
		if (logFile.getName().endsWith(".gz")) inputStream = new GZIPInputStream(inputStream);
		String encoding = UniversalDetector.detectCharset(inputStream);

		// detectCharset consumes input stream
		InputStream newInputStream = Files.newInputStream(logFile.toPath());
		if (logFile.getName().endsWith(".gz")) newInputStream = new GZIPInputStream(newInputStream);
		BufferedReader br;
		if ("WINDOWS-1252".equals(encoding)) {
			br = new BufferedReader(new InputStreamReader(newInputStream, StandardCharsets.ISO_8859_1));
		} else {
			br = new BufferedReader(new InputStreamReader(newInputStream, StandardCharsets.UTF_8));
		}

		final String userSettingLine = "\\[[0-9:]{8}] \\[Client thread/INFO]: Setting user: ";
		linesStream = br.lines().filter(a -> {
			if (tmpPlayerName == null && a.matches(userSettingLine + ".*"))
				tmpPlayerName = a.replaceAll(userSettingLine, "");
			return onlyChat ? a.contains("[CHAT]") : a.matches("\\[[0-9:]{8}] .*");
		});
		playerName = tmpPlayerName;
	}

	private long getCreationTime(File file) {
		try {
			return Files.readAttributes(file.toPath(), BasicFileAttributes.class).creationTime().toMillis();
		} catch (IOException ignored) {
		}
		return 0;
	}

	public String getPlayerName() {
		return playerName;
	}

	public List<MCLogLine> filterLines(String logLineFilterRegex, String lastPlayerName) {
		List<MCLogLine> filteredlogLines = linesStream
				.map(a -> new MCLogLine(getTime(creationTime, a.substring(1, 9)), lastPlayerName,
						a.replaceAll("\\[[0-9:]{8}] \\[Client thread/INFO]: \\[CHAT] ", "").trim(),
						stripColorCodes))
				.filter(a -> a.getText().matches(logLineFilterRegex)).collect(Collectors.toList());
		linesStream.close();
		return filteredlogLines;
	}

	private long getTime(long creationTimeFile, String creationTimeLine) {
		if (!creationTimeLine.matches("[0-9:]{8}"))
			return creationTimeFile;
		String[] array = creationTimeLine.split(":");
		long msFromStartOfDay = (Integer.parseInt(array[0]) * 3600L + Integer.parseInt(array[1]) * 60L
				+ Integer.parseInt(array[2])) * 1000;
		if (startingTimeOfFile == null)
			startingTimeOfFile = msFromStartOfDay;
		return creationTimeFile + (msFromStartOfDay - startingTimeOfFile);
	}
}

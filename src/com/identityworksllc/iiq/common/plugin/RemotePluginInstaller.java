package com.identityworksllc.iiq.common.plugin;

import java.io.BufferedReader;
import java.io.Console;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility to install plugins remotely using the IIQ REST API. This class can has no
 * external dependencies, so can be isolated.
 *
 * Usage: java com.identityworksllc.iiq.common.plugin.RemotePluginInstaller -p /path/to/properties install /path/to/file
 *
 * Commands exit with a non-zero exit code when problems occur.
 *
 * TODO when we migrate this library to minimum JDK 11, use the JDK HTTP client class
 */
public class RemotePluginInstaller {

	private static class Plugin {
		private boolean enabled;
		private String id;
		private String name;
		private String version;

		public Plugin() {

		}

		@Override
		public String toString() {
			StringJoiner joiner = new StringJoiner(", ", "{", "}");
			if ((id) != null) {
				joiner.add("id=\"" + id + "\"");
			}
			if ((name) != null) {
				joiner.add("name=\"" + name + "\"");
			}
			joiner.add("enabled=" + enabled);
			if ((version) != null) {
				joiner.add("version=\"" + version + "\"");
			}
			return joiner.toString();
		}
	}

	enum OutputLevel {
		Debug,
		Info,
		Error
	}

	/**
	 * The main method for this utility. Parses the command line arguments, validates
	 * them, and optionally prompts the user for a password. Then, executes the command
	 * specified if the inputs are valid.
	 *
	 * @param args The arguments to the main method
	 * @throws Exception on any failures at all
	 */
	public static void main(String[] args) throws Exception {

		Properties properties = new Properties();

		String propertiesFile = null;
		String username = null;
		String host = null;
		String command = null;
		String stringPassword = null;
		char[] charPassword = null;

		List<String> commandArgs = new ArrayList<>();

		boolean expectCommandArgs = false;

		// Parse the inputs
		for(int a = 0; a < args.length; a++) {
			String value = args[a];
			if (!expectCommandArgs) {
				if (value.equals("-u") && (a + 1) < args.length) {
					username = args[a + 1].trim();
					a++;
				} else if (value.equals("-h") && (a + 1) < args.length) {
					host = args[a + 1].trim();
					a++;
				} else if (value.equals("-p") && (a + 1) < args.length) {
					propertiesFile = args[a + 1].trim();
					a++;
				} else if (command == null) {
					command = value.trim().toLowerCase(Locale.ROOT);
					expectCommandArgs = true;
				}
			} else {
				commandArgs.add(value);
			}
		}

		if (propertiesFile != null) {
			Path propsPath = Paths.get(propertiesFile);
			if (!Files.exists(propsPath)) {
				throw new FileNotFoundException("No such properties file: " + propertiesFile);
			}

			try (InputStream fis = Files.newInputStream(propsPath)) {
				properties.load(fis);
			}
		}

		if (!properties.isEmpty()) {
			username = properties.getProperty("username", username);
			stringPassword = properties.getProperty("password", stringPassword);
			host = properties.getProperty("url", host);
		}

		boolean valid = validateInputs(username, host, command, commandArgs);

		if (!valid) {
			System.exit(1);
		}

		assert(host != null);
		assert(username != null);

		URI iiq = new URI(host);

		if (!iiq.isAbsolute()) {
			throw new IOException("The specified IIQ URL must be absolute (i.e., end with a /)");
		}

		if (stringPassword == null || stringPassword.trim().isEmpty()) {
			Console javaConsole = System.console();
			if (javaConsole == null) {
				throw new IllegalStateException("Unable to open a Java console; are you in a normal terminal?");
			}

			charPassword = javaConsole.readPassword("Password for IIQ user [" + username + "]: ");

			if (charPassword == null || charPassword.length < 1) {
				// Default to admin
				charPassword = new char[] { 'a', 'd', 'm', 'i', 'n' };
			}
		} else {
			charPassword = stringPassword.toCharArray();
		}

		RemotePluginInstaller pluginInstaller = new RemotePluginInstaller(iiq, username, charPassword);
		int exitCode = 0;

		try {
			if (command.equals("check")) {
				String pluginName = commandArgs.get(0);
				boolean installed = pluginInstaller.isPluginInstalled(pluginName);
				if (!installed) {
					exitCode = 1;
				}

				System.out.println(installed);
			} else if (command.equals("install")) {
				String path = commandArgs.get(0);
				Path pluginPath = Paths.get(path);
				if (!Files.exists(pluginPath)) {
					throw new IOException("Invalid or unreadable plugin ZIP path specified: " + path);
				}
				pluginInstaller.installPlugin(pluginPath);

				System.out.println("Installed plugin");
			} else if (command.equals("get")) {
				String pluginName = commandArgs.get(0);
				Optional<Plugin> plugin = pluginInstaller.getPlugin(pluginName);
				if (plugin.isPresent()) {
					System.out.println(plugin.get());
				} else {
					System.out.println("Not found");
					exitCode = 1;
				}
			}
		} catch(Exception e) {
			output(OutputLevel.Error, e.toString());
			exitCode = 1;
		}

		System.exit(exitCode);
	}

	public static void output(OutputLevel level, String output, Object... variables) {
		if (variables != null && variables.length > 0) {
			output = MessageFormat.format(output, variables);
		}

		if (level == OutputLevel.Debug) {
			System.err.print(" [+] ");
		} else if (level == OutputLevel.Info) {
			System.err.print(" [*] ");
		} else {
			System.err.print(" [!] ");
		}

		System.err.println(output);
	}

	/**
	 * Validates the command line inputs, printing a usage message if they are not valid.
	 *
	 * @param username The provided username
	 * @param host The provided URL
	 * @param command The provided command name
	 * @param commandArgs The provided command arguments
	 * @return True if validation passes and the task should proceed
	 */
	private static boolean validateInputs(String username, String host, String command, List<String> commandArgs) {
		List<String> errors = new ArrayList<>();
		if (username == null || username.trim().isEmpty()) {
			errors.add("Missing required value: username (-u or 'username' property)");
		}

		if (host == null || host.trim().isEmpty()) {
			errors.add("Missing required value: IIQ URL (-h or 'url' property)");
		} else if (!host.startsWith("http")) {
			errors.add("Invalid format: IIQ URL must start with 'http'");
		}

		if (command == null || command.trim().isEmpty()) {
			errors.add("Missing command, valid values are : check, install");
		} else if (!(command.equals("check") || command.equals("install") || command.equals("get"))) {
			errors.add("Invalid command '" + command + "', valid values are : check, install");
		} else if (commandArgs.isEmpty()) {
			errors.add("You must specify one or more arguments for your command: '" + command + "'");
		}

		if (!errors.isEmpty()) {
			for(String error : errors) {
				System.err.println("ERROR: " + error);
			}
			System.err.println();
			System.err.println("Usage: RemotePluginInstaller [-h URL] [-u username] [-p properties] command [commandOptions]");
			System.err.println();
			System.err.println("  Commands: ");
			System.err.println("     check [pluginName]:      Checks whether the plugin is installed and outputs true/false");
			System.err.println("     get [pluginName]:        Outputs a JSON object of the plugin metadata, if it is installed");
			System.err.println("     install [pluginZipPath]: Installs the plugin file at the given path");
			return false;
		} else {
			return true;
		}
	}
	private final URI iiq;
	private final char[] password;
	private final String username;

	public RemotePluginInstaller(URI iiq, String username, char[] password) throws URISyntaxException {
		this.iiq = iiq;
		this.username = username;
		this.password = password;
	}

	public Optional<Plugin> getPlugin(String name) throws IOException {
		URI pluginUrl = this.iiq.resolve("rest/plugins");

		output(OutputLevel.Debug, "Plugin URL: " + pluginUrl);
		output(OutputLevel.Info, "Retrieving plugin data for " + name);

		HttpURLConnection urlConnection = (HttpURLConnection)pluginUrl.toURL().openConnection();
		urlConnection.setDoInput(true);
		urlConnection.setRequestMethod("GET");

		String usernamePasswordString = Base64.getEncoder().encodeToString((username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));

		urlConnection.addRequestProperty("Authorization", "Basic " + usernamePasswordString);

		if (urlConnection.getResponseCode() > 299) {
			throw new IOException("Received response code " + urlConnection.getResponseCode() + " " + urlConnection.getResponseMessage());
		}

		StringBuilder output = new StringBuilder();

		try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getInputStream()))) {
			String line;
			while ((line = reader.readLine()) != null) {
				output.append(line);
				output.append("\n");
			}
		}

		String outputString = output.toString();

		// Time for a crazy regex, because we're determined to have no external dependencies

		Pattern regex = Pattern.compile(
				"\"id\":\\s*\"(.*?)\"," +
						".*?" +
						"\"name\":\\s*\"" + Pattern.quote(name) + "\"," +
						".*?" +
						"\"version\":\\s*\"(.*?)\"," +
						".*?" +
						"\"disabled\":\\s*(.*?),"
		);

		Matcher matcher = regex.matcher(outputString);

		if (matcher.find()) {
			Plugin vo = new Plugin();
			vo.id = matcher.group(1);
			vo.name = name;
			vo.version = matcher.group(2);
			vo.enabled = !Boolean.parseBoolean(matcher.group(3));

			return Optional.of(vo);
		}

		return Optional.empty();
	}
	
	/**
	 * Installs a plugin remotely using base Java8 classes
	 *
	 * @param toUpload The file to upload as a plugin
	 * @throws IOException on any send failures
	 */
	public void installPlugin(Path toUpload) throws IOException {
		URI pluginUrl = this.iiq.resolve("rest/plugins");

		String filename = toUpload.getFileName().toString();

		output(OutputLevel.Debug, "Plugin URL: " + pluginUrl);
		output(OutputLevel.Info, "Installing a new plugin from file " + toUpload);

		String multipartFormBoundary = "----Boundary" + System.currentTimeMillis();

		HttpURLConnection urlConnection = (HttpURLConnection)pluginUrl.toURL().openConnection();
		urlConnection.setDoOutput(true);
		urlConnection.setDoInput(true);
		urlConnection.setRequestMethod("POST");
		urlConnection.addRequestProperty("Content-Type", "multipart/form-data; boundary=" + multipartFormBoundary);

		String usernamePasswordString = Base64.getEncoder().encodeToString((username + ":" + new String(password)).getBytes(StandardCharsets.UTF_8));

		urlConnection.addRequestProperty("Authorization", "Basic " + usernamePasswordString);

		try (OutputStream outputStream = urlConnection.getOutputStream()) {
			try(PrintWriter writer = new PrintWriter(outputStream)) {
				writer.print("\n\n--" + multipartFormBoundary);
				writer.println("Content-Disposition: form-data; name=\"fileName\"");
				writer.println();
				writer.println(filename);
				writer.println("--" + multipartFormBoundary);
				writer.println("Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"");
				writer.println("Content-Type: application/octet-stream");
				writer.println();
				writer.flush();

				try(InputStream is = Files.newInputStream(toUpload)) {
					int bytesRead;
					byte[] buffer = new byte[1024];
					while((bytesRead = is.read(buffer)) != -1) {
					    outputStream.write(buffer, 0, bytesRead);
					}
				}

				outputStream.flush();

				writer.println();
				writer.println("--" + multipartFormBoundary + "--");
				writer.flush();
			}
		}
		if (urlConnection.getResponseCode() > 299) {
			if (urlConnection.getResponseCode() == 400) {
				output(OutputLevel.Error, "Received response code 400, indicating a problem with your uploaded file");
			}

			try (BufferedReader reader = new BufferedReader(new InputStreamReader(urlConnection.getErrorStream()))) {
				String line;
				while((line = reader.readLine()) != null) {
					output(OutputLevel.Error, line);
				}
			}

			throw new IOException("Received response code " + urlConnection.getResponseCode());
		} else {
			output(OutputLevel.Info, "Received response code " + urlConnection.getResponseCode());
		}
	}
	
	/**
	 * Makes a query to the Suggest Service to check whether the plugin is installed. The Suggest service is used because the basic plugin query API does not support names or display names, whereas the Suggester can take a filter.
	 * @param pluginName The plugin name
	 * @return The suggest service
	 * @throws IOException if any failures occur
	 */
	public boolean isPluginInstalled(String pluginName) throws IOException {
		Optional<Plugin> vo = getPlugin(pluginName);
		return vo.isPresent();
	}

	public void uninstallPlugin(String pluginName) throws IOException {

	}

}

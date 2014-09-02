package x.mvmn.gp2srv.service;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.io.IOUtils;

import x.mvmn.log.PrintStreamLogger;
import x.mvmn.log.api.Logger;

public class ExecServiceImpl implements ExecService {

	private volatile Process currentProcess = null;

	private final Object lockObject = new Object();

	private final Logger logger;

	public ExecServiceImpl(Logger logger) {
		this.logger = logger;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see x.mvmn.gp2srv.service.ExecService#execCommandSync(java.lang.String[], java.lang.String[], java.io.File)
	 */
	public ExecResult execCommandSync(final String[] command, final String[] envVars, final File dir) throws IOException {
		final String commandDebugInfo = "'" + Arrays.toString(command) + "' in dir '" + dir.getAbsolutePath() + "'.";
		logger.debug("Command requested " + commandDebugInfo);
		final StringBuilder standardOutput = new StringBuilder();
		final StringBuilder errorOutput = new StringBuilder();
		int processExitValue = 0;
		synchronized (lockObject) {
			logger.debug("Executing command " + commandDebugInfo);
			currentProcess = Runtime.getRuntime().exec(command, envVars, dir);
			final InputStream errorStream = currentProcess.getErrorStream();
			final InputStream stdoutStream = currentProcess.getInputStream();

			boolean processFinished = false;
			while (!processFinished) {
				final List<String> errorLines = IOUtils.readLines(errorStream);
				for (String line : errorLines) {
					errorOutput.append(line).append("\n");
					logger.debug("Command " + commandDebugInfo + " ERROR output: " + line);
				}
				final List<String> resultLines = IOUtils.readLines(stdoutStream);
				for (String line : resultLines) {
					standardOutput.append(line).append("\n");
					logger.debug("Command " + commandDebugInfo + " STANDARD output: " + line);
				}
				try {
					processExitValue = currentProcess.exitValue();
					processFinished = true;
					logger.debug("Command " + commandDebugInfo + " process exited with code " + processExitValue);
				} catch (IllegalThreadStateException e) {
					processFinished = false; // superfluous here, but left to indicate the meaning of the code.
				}
			}

			currentProcess = null;
			logger.debug("Command " + commandDebugInfo + " finished.");
		}

		return new ExecResult(standardOutput.toString(), errorOutput.toString(), processExitValue);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see x.mvmn.gp2srv.service.ExecService#execCommandAsync(x.mvmn.gp2srv.service.ExecServiceImpl.ExecCallback, java.lang.String[], java.lang.String[],
	 * java.io.File)
	 */
	public void execCommandAsync(final ExecCallback callback, final String[] command, final String[] envVars, final File dir) {
		new Thread() {
			@Override
			public void run() {
				try {
					final ExecResult result = execCommandSync(command, envVars, dir);
					if (callback != null) {
						callback.processResult(result);
					}
				} catch (Exception e) {
					if (callback != null) {
						try {
							callback.processError(e);
						} catch (Exception epe) {
							final StringBuilder commandBuilder = new StringBuilder();
							if (command == null) {
								commandBuilder.append("null");
							} else {
								for (String commandPart : command) {
									commandBuilder.append(commandPart).append(" ");
								}
							}
							logger.error("Async call for command " + commandBuilder.toString() + " error processing failed with another error", epe);
						}
					}
				}
			}
		}.start();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see x.mvmn.gp2srv.service.ExecService#isProcessRunning()
	 */
	public boolean isProcessRunning() {
		return currentProcess != null;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see x.mvmn.gp2srv.service.ExecService#getCurrentProcess()
	 */
	public Process getCurrentProcess() {
		return currentProcess;
	}

	public static void main(String[] args) throws Exception {
		ExecServiceImpl svc = new ExecServiceImpl(new PrintStreamLogger(System.out));
		System.out.println(svc.execCommandSync(new String[] { "ls" }, null, null));
		svc.execCommandAsync(null, new String[] { "sleep", "5" }, null, null);
		Thread.sleep(1);
		System.out.println(svc.execCommandSync(new String[] { "/opt/local/bin/gphoto2", "--summary" }, null, null));
	}
}

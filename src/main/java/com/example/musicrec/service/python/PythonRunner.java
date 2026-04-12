package com.example.musicrec.service.python;

import com.example.musicrec.config.AppProperties;
import com.example.musicrec.exception.ExternalProcessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
@RequiredArgsConstructor
public class PythonRunner {

    private final AppProperties props;

    /**
     * Runs python script as an external process.
     *
     * Integration points:
     * - change python executable via app.python.executable
     * - change scripts dir via app.python.scripts-dir
     * - adapt arguments contract in python scripts
     */
    public PythonResult runScript(String scriptName, List<String> args) {
        Path scriptsDir = Path.of(props.getPython().getScriptsDir()).toAbsolutePath().normalize();
        Path scriptPath = scriptsDir.resolve(scriptName);

        List<String> cmd = new ArrayList<>();
        String executable = props.getPython().getExecutable();

        cmd.add(executable);

        // Windows launcher: py -3 script.py
        if ("py".equalsIgnoreCase(executable)) {
            cmd.add("-3");
        }

        cmd.add(scriptPath.toString());
        cmd.addAll(args);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.redirectErrorStream(true);

        try {
            Process process = pb.start();

            ExecutorService es = Executors.newSingleThreadExecutor();
            Future<String> stdoutFuture = es.submit(() -> {
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        sb.append(line).append("\n");
                    }
                }
                return sb.toString();
            });

            boolean finished = process.waitFor(props.getPython().getTimeoutSeconds(), TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new ExternalProcessException(
                        "Python script timed out after " + props.getPython().getTimeoutSeconds() + " seconds: " + scriptName
                );
            }

            int exit = process.exitValue();
            String out = stdoutFuture.get(5, TimeUnit.SECONDS);
            es.shutdownNow();

            if (exit != 0) {
                throw new ExternalProcessException(
                        "Python script failed (exit=" + exit + "): " + scriptName + "\nCommand: " + String.join(" ", cmd) + "\nOutput:\n" + out
                );
            }

            return new PythonResult(exit, out);

        } catch (ExternalProcessException e) {
            throw e;
        } catch (Exception e) {
            throw new ExternalProcessException(
                    "Failed to run python script: " + scriptName + " (" + e.getMessage() + ")",
                    e
            );
        }
    }

    public record PythonResult(int exitCode, String output) {}
}

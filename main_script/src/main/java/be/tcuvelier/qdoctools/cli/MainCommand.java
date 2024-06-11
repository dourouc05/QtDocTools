package be.tcuvelier.qdoctools.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

@SuppressWarnings("WeakerAccess")
@Command(description = "QDocTools", subcommands = {
        TransformCommand.class,
        MergeCommand.class,
        UploadCommand.class,
        QDocCommand.class,
        QDocPublishCommand.class
}, mixinStandardHelpOptions = true, version = "QDocTools 0.1.0")
public class MainCommand implements Callable<Void> {
    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @Override
    public Void call() {
        // Using a subcommand is required, this command is just an umbrella and a place to store
        // global things.
        throw new CommandLine.ParameterException(spec.commandLine(), "Missing required " +
                "subcommand\n");
    }
}

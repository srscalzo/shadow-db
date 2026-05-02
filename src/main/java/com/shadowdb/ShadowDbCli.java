package com.shadowdb;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(
    name = "shadowdb",
    mixinStandardHelpOptions = true,
    version = "shadowdb 1.0.0",
    description = "Preview Flyway migration schema changes before applying them.",
    subcommands = { DiffCommand.class }
)
public class ShadowDbCli {

    public static void main(String[] args) {
        int exitCode = new CommandLine(new ShadowDbCli()).execute(args);
        System.exit(exitCode);
    }
}

/*
 * This file is part of the DITA Open Toolkit project.
 *
 * Copyright 2019 Jarno Elovirta
 *
 * See the accompanying LICENSE file for applicable license.
 */

/* Derived from Apache Ant. */
package org.dita.dost.invoker;

import org.apache.tools.ant.BuildException;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Command line arguments.
 *
 * @since 3.4
 */
final class Arguments {

    final boolean useColor;
    final File logFile;
    /**
     * Our current message output status. Follows Project.MSG_XXX.
     */
    final int msgOutputLevel;
    /**
     * File that we are using for configuration.
     */
    final File buildFile;
    /**
     * Run integrator
     */
    final boolean install;
    /**
     * Plug-in installation file. May be either a system path or a URL.
     */
    final String installFile;
    /**
     * Project file
     */
    final File projectFile;
    /**
     * Plug-in uninstall ID.
     */
    final String uninstallId;
    final List<String> inputs;
    /**
     * The build targets.
     */
    final Vector<String> targets;
    /**
     * Names of classes to add as listeners to project.
     */
    final Vector<String> listeners;
    /**
     * File names of property files to load on startup.
     */
    final Vector<String> propertyFiles;
    /**
     * Indicates whether this build is to support interactive input
     */
    final boolean allowInput;
    /**
     * keep going mode
     */
    final boolean keepGoingMode;
    /**
     * The Ant logger class. There may be only one logger. It will have the
     * right to use the 'out' PrintStream. The class must implements the
     * BuildLogger interface.
     */
    final String loggerClassname;
    /**
     * The Ant InputHandler class. There may be only one input handler.
     */
    final String inputHandlerClassname;
    /**
     * Whether or not output to the log is to be unadorned.
     */
    final boolean emacsMode;
    /**
     * optional thread priority
     */
    final Integer threadPriority;
    /**
     * proxy flag: default is false
     */
    final boolean proxy;
    final boolean justPrintUsage;
    final boolean justPrintVersion;
    final boolean justPrintDiagnostics;
    final boolean justPrintPlugins;
    final boolean justPrintTranstypes;
    final boolean justPrintDeliverables;
    final Map<String, Object> definedProps;

    public Arguments(boolean useColor, int msgOutputLevel, File buildFile, boolean install, String installFile,
                     File projectFile, String uninstallId, List<String> inputs, Vector<String> targets,
                     Vector<String> listeners, Vector<String> propertyFiles, boolean allowInput, boolean keepGoingMode,
                     String loggerClassname, String inputHandlerClassname, boolean emacsMode, Integer threadPriority,
                     boolean proxy, boolean justPrintUsage, boolean justPrintVersion, boolean justPrintDiagnostics,
                     boolean justPrintPlugins, boolean justPrintTranstypes, boolean justPrintDeliverables,
                     File logFile, Map<String, Object> definedProps) {
        this.useColor = useColor;
        this.msgOutputLevel = msgOutputLevel;
        this.buildFile = buildFile;
        this.install = install;
        this.installFile = installFile;
        this.projectFile = projectFile;
        this.uninstallId = uninstallId;
        this.inputs = inputs;
        this.targets = targets;
        this.listeners = listeners;
        this.propertyFiles = propertyFiles;
        this.allowInput = allowInput;
        this.keepGoingMode = keepGoingMode;
        this.loggerClassname = loggerClassname;
        this.inputHandlerClassname = inputHandlerClassname;
        this.emacsMode = emacsMode;
        this.threadPriority = threadPriority;
        this.proxy = proxy;
        this.justPrintUsage = justPrintUsage;
        this.justPrintVersion = justPrintVersion;
        this.justPrintDiagnostics = justPrintDiagnostics;
        this.justPrintPlugins = justPrintPlugins;
        this.justPrintTranstypes = justPrintTranstypes;
        this.justPrintDeliverables = justPrintDeliverables;
        this.logFile = logFile;
        this.definedProps = definedProps;
    }

    static abstract class Argument {
        final String property;

        Argument(final String property) {
            this.property = property;
        }

        abstract String getValue(final String value);
    }

    static class StringArgument extends Argument {
        StringArgument(final String property) {
            super(property);
        }

        @Override
        String getValue(final String value) {
            return value;
        }
    }

    static class BooleanArgument extends Argument {
        final String trueValue;
        final String falseValue;

        BooleanArgument(final String property, final String trueValue, final String falseValue) {
            super(property);
            this.trueValue = trueValue;
            this.falseValue = falseValue;
        }

        @Override
        String getValue(final String value) {
            switch (value.toLowerCase()) {
                case "true":
                case "yes":
                case "on":
                case "1":
                    return trueValue;
                default:
                    return falseValue;
            }
        }
    }

    static class EnumArgument extends Argument {
        final Set<String> values;

        EnumArgument(final String property, final Set<String> values) {
            super(property);
            this.values = values;
        }

        @Override
        String getValue(final String value) {
            if (!values.contains(value)) {
                throw new BuildException("Invalid value for property " + property + ": " + value);
            }
            return value;
        }
    }

    static class FileArgument extends Argument {
        FileArgument(final String property) {
            super(property);
        }

        @Override
        String getValue(final String value) {
            return new File(value).getPath();
        }
    }

    static class AbsoluteFileArgument extends Argument {
        AbsoluteFileArgument(final String property) {
            super(property);
        }

        @Override
        String getValue(final String value) {
            return new File(value).getAbsolutePath();
        }
    }

    static class AbsoluteFileListArgument extends Argument {
        AbsoluteFileListArgument(final String property) {
            super(property);
        }

        @Override
        String getValue(final String value) {
            return Arrays.stream(value.split(File.pathSeparator))
                    .map(oneFile -> new File(oneFile).getAbsolutePath())
                    .collect(Collectors.joining(File.pathSeparator));
        }
    }

    static class FileOrUriArgument extends Argument {
        FileOrUriArgument(final String property) {
            super(property);
        }

        @Override
        String getValue(final String value) {
            final File f = new File(value);
            if (f.exists()) {
                return f.getAbsolutePath();
            } else {
                return value;
            }
        }
    }

}

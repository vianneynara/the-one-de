# The ONE v1.4.1 - Readme

The ONE is a Opportunistic Network Environment simulator which provides a powerful tool for generating mobility traces, running DTN messaging simulations with different routing protocols, and visualizing both simulations interactively in real-time and results after their completion.

## Quick start

### Compiling

You can compile ONE from the source code using the included `compile.bat` script. That should work both in Windows and Unix/Linux environment with Java 6 JDK or later.

If you want to use Eclipse for compiling the ONE, since version 1.1.0 you need to include some jar libraries in the project's build path. The libraries are located in the `lib` folder. To include them in Eclipse:

1. Select from menus: `Project -> Properties -> Java Build Path`
2. Go to "Libraries" tab
3. Click "Add JARs..."
4. Select "DTNConsoleConnection.jar" under the "lib" folder
5. Add the "ECLA.jar" the same way
6. Press "OK".

Now Eclipse should be able to compile the ONE without warnings.

### Running

ONE can be started using the included `one.bat` (for Windows) or `one.sh` (for Linux/Unix) script.

**Synopsis:**
```
./one.sh [-b runcount] [conf-files]
```

**Options:**
- `-b` Run simulation in batch mode. Doesn't start GUI but prints information about the progress to terminal. The option must be followed by the number of runs to perform in the batch mode or by a range of runs to perform, delimited with a colon (e.g, value 2:4 would perform runs 2, 3 and 4).

**Parameters:**
- `conf-files`: The configuration file names where simulation parameters are read from. Any number of configuration files can be defined and they are read in the order given in the command line.

## Configuring

All simulation parameters are given using configuration files. These files are normal text files that contain key-value pairs. Syntax for most of the variables is:
```
Namespace.key = value
```

For detailed configuration instructions, including movement models, routing modules, reports, host groups, and more, refer to the full README text.

## GUI

The GUI's main window is divided into three parts: the playfield view, node selection, and logging/breakpoints. It allows for control over simulation speed, zoom, and provides detailed information on nodes and messages.

## DTN2 Reference Implementation Connectivity

DTN2 connectivity allows bundles to be passed between the ONE and any number of DTN2 routers through DTN2's External Convergence Layer Interface.

**To enable this functionality:**

1. DTN2 must be compiled and configured with ECL support enabled.
2. DTN2Events event generator must be configured to be loaded into ONE as an events class.
3. DTN2Reporter must be configured and loaded into one as a report class.
4. DTN2 connectivity configuration file must be configured as `DTN2.configFile`.

**Example Configuration:**
```
Events.nrof = 1
Events1.class = DTN2Events
Report.nrofReports = 1
Report.report1 = DTN2Reporter
DTN2.configFile = cla.conf
```

## Toolkit

The simulation package includes a folder called "toolkit" that contains scripts for generating input and processing the output of the simulator. Scripts are written with Perl and some post-processing scripts use gnuplot for creating graphics.

**Included scripts:**
- `getStats.pl`
- `ccdfPlotter.pl`
- `createCreates.pl`
- `dtnsim2parser.pl` and `transimsParser.pl`

For detailed usage of these scripts, refer to the toolkit section in the full README text.
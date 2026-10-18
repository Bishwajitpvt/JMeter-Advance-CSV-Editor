# JJMeter-Advance-CSV-Editor Plugin

**Edit, create, and manage CSV test data directly inside Apache JMeter.**

A custom, GUI-based alternative to **CSV Data Set Config** that adds an editable table and Excel-style multi-cell paste while using JMeter's standard `CSVDataSet` test element for runtime CSV consumption.

> **Current version:** 1.3.0  
> **Target compatibility:** Apache JMeter 5.6.2 · Java 8 · Maven 3.9.2  
> **Status:** Source build provided; validate on your own JMeter installation before team-wide use.

## Features

| Feature | Description |
| --- | --- |
| CSV preview | Display CSV records in a scrollable table, inside JMeter. |
| Edit cells | Modify values directly in the table. |
| Add rows | Append blank records to the displayed data. |
| Delete rows | Select rows and remove them after confirmation. |
| Save changes | Write edits back to the original CSV file. |
| New CSV | Choose a filename, define headers, and create blank data rows. |
| Excel-style paste **(v1.3.0)** | Paste multiple rows and columns from Excel, Sheets, or another tab-separated clipboard source. |
| Automatic table expansion | Add rows and columns when a pasted range exceeds the table boundaries. |
| Clipboard shortcut | Press **Ctrl+V** while the table is focused, or use **Paste from Excel (Ctrl+V)**. |
| Unsaved-change indicator | Indicates that edits have not yet been written to the CSV. |
| External edit protection | Cancel saving if the source CSV changed on disk since loading it. |
| Standard CSV Data Set behavior | Runtime filename, encoding, delimiter, variable names, quoted data, EOF and sharing settings are forwarded to JMeter's CSV engine. |

### Standard JMeter settings

The plugin exposes the same core configuration properties as CSV Data Set Config:

- **Filename** — CSV file path.
- **File encoding** — optional character encoding.
- **Variable Names** — comma-separated names; if left blank, JMeter uses the first line as variable names.
- **Ignore First Line** — applicable when explicit variable names are supplied.
- **Delimiter** — default `,`.
- **Allow quoted data?** — standard JMeter CSV parsing option.
- **Recycle on EOF?** — start reading the file again at end-of-file.
- **Stop thread on EOF?** — stop the thread when EOF is reached, according to JMeter behavior.
- **Sharing mode** — All threads / Current thread group / Current thread / Identifier.

## Requirements

- Apache JMeter **5.6.2** (project target)
- Java **8** or a suitable newer Java runtime supported by JMeter
- Apache Maven (tested by the project owner with **3.9.2**) to build the source
- Permission to install JAR files into the JMeter `lib/ext` directory

## Build from source

Open **Command Prompt in the extracted project folder that contains `pom.xml`**, not inside `src/main/java`.

```cmd
mvn -s settings-central.xml clean package
```

The included `settings-central.xml` uses Maven Central directly. **Only use this configuration if your organization's policies allow bypassing its Nexus mirror.** Otherwise use the approved Maven settings:

```cmd
mvn clean package
```

Successful packaging creates:

```text
target/jmeter-csv-editor-1.3.0.jar
```

Maven Shade may also create `original-jmeter-csv-editor-1.3.0.jar`. **Install the non-`original-` JAR** because it includes the packaged dependency classes.

## Install in JMeter

1. Close JMeter.
2. Remove any **older `jmeter-csv-editor-*.jar`** from `<JMETER_HOME>\lib\ext`.
3. Copy `target\jmeter-csv-editor-1.3.0.jar` to `<JMETER_HOME>\lib\ext`.
4. Restart JMeter.
5. In a Test Plan or Thread Group, go to **Add → Config Element → CSV Data Set Config (Editable)**.

**Do not install both the current and `original-` JAR, or multiple plugin versions.**

## Quick start: open and edit a CSV

1. Add **CSV Data Set Config (Editable)**.
2. Type the full **Filename** or select **Browse...**.
3. Review the previewed records in the CSV table.
4. Double-click a cell to change its value; use **Add Row** or **Delete Selected Rows** as needed.
5. Select **Save changes to CSV** to write the data back to the source file.
6. Configure JMeter CSV settings and save the test plan.

**Important:** The editable preview displays the CSV's first record as a normal table row. For CSVs with headers, avoid accidentally deleting or changing that record. Editing the table does not update disk until you save.

## Create a new CSV

1. Click **New CSV...** beside the Filename field.
2. Choose a directory and a new `.csv` filename.
3. Enter unique column headers, for example:

   ```text
   Username,Password,AccountNo
   ```
4. Choose the number of blank data rows and confirm.
5. Fill the rows manually or paste a range from Excel.
6. Click **Save changes to CSV** after editing.

The new CSV is **created immediately** with a header record and optional blank rows. The plugin refuses to overwrite an existing file during **New CSV** creation.

## Excel-style copy and paste (v1.3.0)

1. In Excel, LibreOffice Calc, or a compatible spreadsheet, select a rectangular set of cells.
2. Press **Ctrl+C**.
3. In JMeter's CSV editor table, click the **top-left destination cell**.
4. Press **Ctrl+V**, or select **Paste from Excel (Ctrl+V)**.
5. Inspect the preview and select **Save changes to CSV**.

Example source range:

| Username | Password | AccountNo |
| --- | --- | --- |
| user01 | pass01 | 10001 |
| user02 | pass02 | 10002 |
| user03 | pass03 | 10003 |

If the destination starts at row 2, column 1, the pasted values begin at that location. Rows and columns are added automatically when required.

**Paste behavior and limits:**

- Spreadsheet clipboard content is interpreted as **tab-separated columns** and newline-separated rows, supporting quoted cells containing tabs or line breaks.
- Text containing commas but **no tabs** is treated as a single column per line; for multi-column data from a plain CSV file, open it in a spreadsheet and copy the cells.
- Paste starts at the selected table cell; if nothing is selected, it starts at the beginning.
- The editor has a defensive size limit of **1,000,000 rows and 10,000 columns** for pasted ranges. This is not a promise that very large files will fit comfortably in memory.
- Adding new columns can change your CSV's variable structure. Verify the header row and JMeter **Variable Names** settings after pasting.
- A paste is not permanent until you select **Save changes to CSV**.

### CSV variables in JMeter

For the following CSV:

```csv
Username,Password
user01,pass01
user02,pass02
```

If **Variable Names** is blank and **Ignore First Line** is unchecked, JMeter uses the first record as variable names. In samplers, refer to:

```text
${Username}
${Password}
```

If you enter explicit variable names, use **Ignore First Line** appropriately to prevent the header from being read as test data.

## Sharing and EOF behavior

The custom GUI configures JMeter's standard `CSVDataSet` runtime element; record distribution is therefore managed by JMeter. Validate your chosen settings with a small test before a high-concurrency run:

| Setting | What to verify |
| --- | --- |
| All threads | Threads share the intended CSV cursor. |
| Current thread group | Each group has the intended sharing scope. |
| Current thread | Each thread reads with its own scope. |
| Identifier | Elements using the same identifier share as intended. |
| Recycle / Stop thread on EOF | Behavior matches the test plan's desired end-of-file handling. |

For load-test scripts, test **non-GUI execution** after verifying the GUI editor and save workflow.

## Safety notes

- **Back up important CSVs** before bulk edits or pastes.
- **Do not edit CSV files while a load test is running**; the JMeter engine may already have the file open.
- If the CSV is changed by another application after being loaded, **Save** is cancelled to protect external changes; reload carefully after preserving any unsaved work.
- CSV files containing credentials, personal data, tokens or customer records should **not** be committed to a public GitHub repository. Use synthetic examples only.
- Large CSVs are displayed in a Swing table; there is **no virtualized/lazy-loaded grid** in v1.3.0. For exceptionally large files, use a dedicated bulk-data tool.

## Troubleshooting

| Problem | What to check |
| --- | --- |
| Plugin menu item missing | Confirm the current JAR is in `lib/ext`, remove older versions, and fully restart JMeter. Review `jmeter.log` for plugin loading errors. |
| `settings-central.xml` not found | Run Maven from the directory containing both `pom.xml` and `settings-central.xml`. |
| Nexus cannot download a Maven plugin | Check company Nexus connectivity; if permitted, use the supplied Maven Central settings. |
| `mvn` not recognized | Verify Maven installation and your `PATH`. |
| Excel paste appears in one cell | Select the JTable cell rather than editing text inside the cell; try the Paste button. Ensure the copied range is tabular. |
| Paste adds unexpected columns | Check the selected starting cell and the number of source columns. Inspect the saved header/variable mapping. |
| Save reports an external change | Another program altered the file. Preserve edits, reload, and apply them to the latest copy. |
| CSV headers are passed as test values | Check **Variable Names** and **Ignore First Line**. |

## Project structure

```text
jmeter-csv-editor-1.3.0/
├── pom.xml
├── settings-central.xml
├── README.md
├── example.csv
└── src/
    └── main/
        └── java/
            └── com/example/jmeter/
                └── EditableCsvDataSetGui.java
```

For GitHub, ignore Maven output using `.gitignore`:

```gitignore
/target/
*.class
.idea/
.vscode/
*.iml
*.log
```

The supplied `settings-central.xml` contains no credentials, but review local settings before committing. Commit source files and the README; distribute the compiled JAR as a **GitHub Release asset** rather than committing build outputs.

## Release history

| Version | Main addition |
| --- | --- |
| 1.0.0 | View, edit, delete and save CSV records in JMeter. |
| 1.1.0 | More consistent, user-friendly configuration and table layout. |
| 1.2.0 | Create a new CSV and enter data from the JMeter UI. |
| 1.3.0 | Excel-style multi-row/multi-column paste with automatic table expansion. |

## Known limitations / possible future improvements

- Undo/Redo and Save As are not included in this version.
- Multi-cell **copy from** the plugin table is not documented as supported; the new feature is **paste into** the table.
- No lazy-loaded paging for huge CSVs.
- No automatic validation of data uniqueness or sensitive content.

## Contributing

For changes, open an issue describing the JMeter version, Java version, how to reproduce the problem, and any non-sensitive log excerpt. Keep fixes compatible with Java 8 and JMeter 5.6.2 unless changing the compatibility target intentionally.

## License

No license file is included in this source archive. Add a license approved by the project owner before declaring it open source or inviting external redistribution.

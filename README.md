# JMeter-Advance-CSV-Editor Plugin

**Edit, create, and manage CSV test data directly inside Apache JMeter.**

A custom, GUI-based alternative to **CSV Data Set Config** that adds an editable table and Excel-style multi-cell paste while using JMeter's standard `CSVDataSet` test element for runtime CSV consumption.

> **Current version:** 1.4  
> **Target compatibility:** Apache JMeter 5.6.2 and 5.6.3 · Java 8 · Maven 3.9.2  
> **Status:** Source build provided; validate on your own JMeter installation before team-wide use.


## v1.4 — Quoted-data consistency fix

This release resolves the difference between the GUI preview and JMeter runtime when **Allow quoted data?** is enabled or disabled:

| Runtime setting | Editor preview and saving behavior |
| --- | --- |
| **Checked** | Uses JMeter's `CSVSaveService.csvReadFile` parser, including quoted commas, `""` escaped quotes and embedded line breaks. On save, standard CSV escaping is applied. |
| **Unchecked** | Uses JMeter's `JOrphanUtils.split` on each physical line. Double quotes are literal characters, not special escaping. On save, literal quotes are preserved without extra escaping. |

When **quoted data is disabled**, editing or pasting a value containing the configured delimiter or a newline produces a clear validation error on Save, **before the original file is replaced**. Such a value cannot be represented as one field in JMeter's unquoted parsing mode. To store it, save/discard current edits, enable **Allow quoted data?**, and re-open/reload the CSV before applying that edit.

Switching **Allow quoted data?** automatically refreshes the preview when there are no unsaved edits; when edits are unsaved, the toggle is reverted and the user is warned. This prevents unintentional reinterpretation of the current editor table.

### Sample CSV (quoted mode enabled)

```csv
Username,Notes,Description
user01,"Mumbai, India","Said ""Hello"""
user02,"Pune, India","Address line 1
Address line 2"
```

**Use `Allow quoted data? = True` in JMeter** to read these values as three fields per logical record. When disabled, JMeter treats the quote marks literally and splits at physical newlines.

### Tests included with the v1.4 source

Run tests and build together:

```powershell
mvn -s settings-central.xml clean package
```

For JMeter 5.6.3 on PowerShell:

```powershell
mvn -s settings-central.xml "-Djmeter.version=5.6.3" clean package
```

Automated JUnit tests in `src/test` cover quoted commas, escaped quotes, multiline fields, trailing blanks, Unicode, unquoted literal quotes, alternative delimiters, validation before overwrite, and blank quoted fields. The project must be built in an environment able to resolve JMeter dependencies. A passing unit test suite does not replace a GUI check and a real JMeter load test.

## Manual verification: quoted-data consistency (v1.4)

Test against **a copy of your CSV**, not production data. Use the same **Allow quoted data?** option in the editor and the JMeter test plan.

### Test A — quoted data enabled

Create `quoted_data_test.csv` as UTF-8:

```csv
Username,City,Remarks,Amount
user01,"Mumbai, India","Normal quoted comma",100
user02,Pune,"Hello ""JMeter""",200
user03,Delhi,"Payment
processed successfully",300
user04,"New York, USA","Special, characters",400
user05,Chennai,"",500
```

Add **CSV Data Set Config (Editable)** and set:

| Field | Value |
| --- | --- |
| Filename | Full path to `quoted_data_test.csv` |
| Variable Names | Leave blank (first record supplies the names) |
| Ignore First Line | False |
| Delimiter | `,` |
| Allow quoted data? | **True** |
| Recycle on EOF? | False |
| Stop thread on EOF? | True |
| Sharing mode | All threads |

**Expected editor preview:** five data records after the header, four columns, `Mumbai, India` in a single cell, `Hello "JMeter"` as the logical text, and `Payment`/`processed successfully` separated by an embedded newline *inside one cell*.

**Runtime check:** add a **Debug Sampler** and **View Results Tree** to a Thread Group with one thread and five loops. Inspect the CSV variables for each loop. The editor preview and Debug Sampler values should match, including the multiline and empty values. Note that JMeter's debug display may visually escape or reformat newline characters.

### Test B — quoted data disabled

Create `unquoted_test.csv`:

```csv
Username,City,Remarks,Amount
user01,Mumbai,Hello "JMeter",100
user02,Pune,Normal text,200
```

Set **Allow quoted data? = False**. Quotes inside the Remarks value should remain literal in both preview and runtime. Try changing the City cell to `Mumbai, India` and saving; v1.4 should **reject the save** because the delimiter would split the field in unquoted mode. Confirm the on-disk CSV is unchanged.

### Test C — edit, save, reload

Return to Test A with quoted mode **True**, change City to `Pune, Maharashtra` and Remarks to `Payment "Approved"`, and save. Reload and confirm the logical cell values survive. An appropriate CSV representation is:

```csv
user01,"Pune, Maharashtra","Payment ""Approved""",100
```

### Acceptance checklist

- [ ] Quoted comma remains in one cell.
- [ ] Doubled quotes decode and re-encode correctly.
- [ ] Multiline quoted field remains one logical row.
- [ ] Empty quoted field is preserved.
- [ ] Unquoted literal quotes are not modified.
- [ ] Unsafe unquoted delimiter/newline saves are blocked without changing the original file.
- [ ] Saved quoted values reload correctly.
- [ ] Native JMeter variable values agree with the editor preview.
- [ ] Repeat the smoke test in both JMeter 5.6.2 and 5.6.3.

**Validation scope:** The package includes Java regression tests, but the README does not claim that the above GUI/runtime scenarios have been executed on your machine.

## Features

| Feature | Description |
| --- | --- |
| CSV preview | Display CSV records in a scrollable table, inside JMeter. |
| Edit cells | Modify values directly in the table. |
| Add rows | Append blank records to the displayed data. |
| Delete rows | Select rows and remove them after confirmation. |
| Save changes | Write edits back to the original CSV file. |
| New CSV | Choose a filename, define headers, and create blank data rows. |
| Excel-style paste **(v1.3.0+)** | Paste multiple rows and columns from Excel, Sheets, or another tab-separated clipboard source. |
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

- Apache JMeter **5.6.2 or 5.6.3** (build defaults to 5.6.2 APIs)
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

### Choose the JMeter API version

By default the project compiles against **5.6.2**, the oldest target, so the resulting plugin can be installed on JMeter **5.6.2 or 5.6.3** (subject to validation in your environment):

```cmd
mvn -s settings-central.xml clean package
```

To explicitly compile against **5.6.3** (the quotes also prevent PowerShell from splitting the property):

```powershell
mvn -s settings-central.xml "-Djmeter.version=5.6.3" clean package
```

**Important:** A JAR compiled specifically against 5.6.3 should not be assumed to work on 5.6.2. To maintain one distributable JAR, use the default 5.6.2 build, keep JMeter dependencies as `provided`, and validate the resulting JAR on both JMeter releases. Java 8 builds require `source`/`target` rather than `--release`.

Successful packaging creates:

```text
target/jmeter-csv-editor-1.4.jar
```

Maven Shade may also create `original-jmeter-csv-editor-1.4.jar`. **Install the non-`original-` JAR** because it includes the packaged dependency classes.

## Install in JMeter

1. Close JMeter.
2. Remove any **older `jmeter-csv-editor-*.jar`** from `<JMETER_HOME>\lib\ext`.
3. Copy `target\jmeter-csv-editor-1.4.jar` to `<JMETER_HOME>\lib\ext`.
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

## Excel-style copy and paste (v1.3.0+)

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
- Large CSVs are displayed in a Swing table; there is **no virtualized/lazy-loaded grid** in v1.4. For exceptionally large files, use a dedicated bulk-data tool.

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
jmeter-csv-editor-1.4/
├── pom.xml
├── settings-central.xml
├── README.md
├── example.csv
└── src/
    ├── main/java/com/example/jmeter/
    │   ├── EditableCsvDataSetGui.java
    │   └── CsvEditorCodec.java
    └── test/java/com/example/jmeter/
        └── CsvEditorCodecTest.java
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
| 1.3.1 | Configurable JMeter 5.6.2 / 5.6.3 build target. |
| **1.4** | **Quoted/unquoted preview and save aligned to native JMeter parsing; JUnit regression tests.** |

## Known limitations / possible future improvements

- Undo/Redo and Save As are not included in this version.
- Multi-cell **copy from** the plugin table is not documented as supported; the new feature is **paste into** the table.
- No lazy-loaded paging for huge CSVs.
- No automatic validation of data uniqueness or sensitive content.

## Contributing

For changes, open an issue describing the JMeter version, Java version, how to reproduce the problem, and any non-sensitive log excerpt. Keep fixes compatible with Java 8 and JMeter 5.6.2 unless changing the compatibility target intentionally.

## License

No license file is included in this source archive. Add a license approved by the project owner before declaring it open source or inviting external redistribution.


## Cross-version smoke test (5.6.2 / 5.6.3)

For each JMeter installation, place only `jmeter-csv-editor-1.4.jar` in `lib/ext` (remove any prior plugin versions), restart JMeter, and confirm:

1. **Add → Config Element → CSV Data Set Config (Editable)** is present.
2. Open a sample CSV; edit a cell and save; confirm that the file changes.
3. Select multiple rows and delete; save and reopen to confirm.
4. Create a new CSV with headers and rows.
5. Paste a 2×3 Excel selection with Ctrl+V; save and reopen.
6. Run a 2-thread test reading CSV variables; check values, EOF behavior, and sharing mode.
7. Save and reopen the JMX file; confirm CSV settings persist.

This source package has **not** been independently compiled or runtime-verified for both versions in the authoring environment.

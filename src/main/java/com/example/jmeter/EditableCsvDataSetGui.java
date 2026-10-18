package com.example.jmeter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.apache.jmeter.config.CSVDataSet;
import org.apache.jmeter.config.gui.AbstractConfigGui;
import org.apache.jmeter.gui.util.MenuFactory;
import org.apache.jmeter.services.FileServer;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.testelement.property.BooleanProperty;
import org.apache.jmeter.testelement.property.StringProperty;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.*;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;

/** CSV Data Set Config with an embedded, on-disk CSV editor. */
public final class EditableCsvDataSetGui extends AbstractConfigGui {
    private static final long serialVersionUID = 1L;
    private final JTextField filename = new JTextField(45);
    private final JTextField encoding = new JTextField(12);
    private final JTextField variableNames = new JTextField(25);
    private final JTextField delimiter = new JTextField(",", 7);
    private final JCheckBox ignoreFirstLine = new JCheckBox("Ignore first line (only when variable names are provided)");
    private final JCheckBox quotedData = new JCheckBox("Allow quoted data?", false);
    private final JCheckBox recycle = new JCheckBox("Recycle on EOF?", true);
    private final JCheckBox stopThread = new JCheckBox("Stop thread on EOF?", false);
    private final JComboBox<String> sharing = new JComboBox<>(new String[]{"All threads", "Current thread group", "Current thread", "Identifier"});
    private final JTextField identifier = new JTextField(12);
    private final JLabel status = new JLabel("Choose a CSV file to preview its contents.");
    private final DefaultTableModel model = new DefaultTableModel() {
        @Override public boolean isCellEditable(int row, int column) { return true; }
    };
    private final JTable table = new JTable(model);
    private Path loadedPath;
    private long loadedSize, loadedModified;
    private byte[] loadedHash;
    private boolean dirty, suppress;
    private final JButton save = new JButton("Save changes to CSV");
    private final JButton pasteButton = new JButton("Paste from Excel (Ctrl+V)");

    private final JButton addRowButton = new JButton("Add Row");
    private final JButton deleteRowsButton = new JButton("Delete Selected Rows");
    private final JButton refreshButton = new JButton("Refresh Preview");
    private final javax.swing.Timer previewTimer = new javax.swing.Timer(450, e -> {
        if (!dirty && !suppress) loadCsv();
    });

    public EditableCsvDataSetGui() {
        setLayout(new BorderLayout(0, 8));
        setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        previewTimer.setRepeats(false);

        JPanel settings = new JPanel(new GridBagLayout());
        settings.setBorder(BorderFactory.createTitledBorder("CSV Data Set Configuration"));
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(3, 8, 3, 8);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;

        JButton browseButton = new JButton("Browse...");
        browseButton.addActionListener(e -> browse());
        JButton newCsvButton = new JButton("New CSV...");
        newCsvButton.setToolTipText("Create a new CSV with named columns, then edit it below");
        newCsvButton.addActionListener(e -> createCsv());
        JPanel fileControls = new JPanel(new BorderLayout(6, 0));
        fileControls.add(filename, BorderLayout.CENTER);
        JPanel fileButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        fileButtons.add(browseButton);
        fileButtons.add(newCsvButton);
        fileControls.add(fileButtons, BorderLayout.EAST);
        formRow(settings, c, 0, "Filename:", fileControls);
        formRow(settings, c, 1, "File encoding:", encoding);
        formRow(settings, c, 2, "Variable names:", variableNames);
        formRow(settings, c, 3, "Delimiter:", delimiter);
        formRow(settings, c, 4, "", ignoreFirstLine);
        formRow(settings, c, 5, "", quotedData);
        formRow(settings, c, 6, "", recycle);
        formRow(settings, c, 7, "", stopThread);

        JPanel shareControls = new JPanel(new BorderLayout(6, 0));
        shareControls.add(sharing, BorderLayout.WEST);
        shareControls.add(identifier, BorderLayout.CENTER);
        identifier.setToolTipText("Only used when sharing mode is Identifier");
        sharing.addActionListener(e -> identifier.setEnabled(sharing.getSelectedIndex() == 3));
        formRow(settings, c, 8, "Sharing mode:", shareControls);
        identifier.setEnabled(false);

        JPanel upper = new JPanel(new BorderLayout(0, 6));
        upper.add(makeTitlePanel(), BorderLayout.NORTH);
        upper.add(settings, BorderLayout.CENTER);
        add(upper, BorderLayout.NORTH);

        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        table.setRowHeight(Math.max(25, table.getRowHeight() + 6));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setCellSelectionEnabled(true);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(true);
        table.setFillsViewportHeight(true);
        table.getTableHeader().setReorderingAllowed(false);
        table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
        table.setPreferredScrollableViewportSize(new Dimension(650, 280));
        model.addTableModelListener(e -> {
            if (!suppress) { dirty = true; updateStatus(); }
        });
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());

        refreshButton.addActionListener(e -> {
            if (mayDiscard()) { dirty = false; loadCsv(); }
        });
        addRowButton.addActionListener(e -> addRow());
        deleteRowsButton.addActionListener(e -> deleteRows());
        pasteButton.addActionListener(e -> pasteClipboard());
        table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(
                KeyStroke.getKeyStroke(KeyEvent.VK_V, InputEvent.CTRL_DOWN_MASK), "pasteExcelBlock");
        table.getActionMap().put("pasteExcelBlock", new AbstractAction() {
            @Override public void actionPerformed(ActionEvent e) { pasteClipboard(); }
        });
        save.addActionListener(e -> saveCsv());

        JPanel actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 7, 4));
        actions.add(refreshButton);
        actions.add(addRowButton);
        actions.add(pasteButton);
        actions.add(deleteRowsButton);
        actions.add(save);

        JPanel editor = new JPanel(new BorderLayout(0, 4));
        editor.setBorder(BorderFactory.createTitledBorder("CSV File Editor — edit cells, then save to update the source file"));
        editor.add(actions, BorderLayout.NORTH);
        editor.add(new JScrollPane(table), BorderLayout.CENTER);
        status.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
        editor.add(status, BorderLayout.SOUTH);
        add(editor, BorderLayout.CENTER);
        save.setEnabled(false);
        updateButtons();

        filename.getDocument().addDocumentListener(reloadOnChange());
        delimiter.getDocument().addDocumentListener(reloadOnChange());
        encoding.getDocument().addDocumentListener(reloadOnChange());
    }

    private void formRow(JPanel panel, GridBagConstraints c, int row, String label, JComponent control) {
        c.gridy = row; c.gridx = 0; c.weightx = 0;
        JLabel text = new JLabel(label);
        text.setPreferredSize(new Dimension(135, 23));
        panel.add(text, c);
        c.gridx = 1; c.weightx = 1;
        panel.add(control, c);
    }

    private DocumentListener reloadOnChange() {
        return new DocumentListener() {
            private void change() {
                if (suppress) return;
                if (dirty) {
                    status.setText("Unsaved edits: save before switching CSV files or settings.");
                    return;
                }
                previewTimer.restart();
            }
            public void insertUpdate(DocumentEvent e) { change(); }
            public void removeUpdate(DocumentEvent e) { change(); }
            public void changedUpdate(DocumentEvent e) { change(); }
        };
    }

    private void updateButtons() {
        boolean hasFile = loadedPath != null;
        addRowButton.setEnabled(hasFile);
        pasteButton.setEnabled(hasFile);
        deleteRowsButton.setEnabled(hasFile && table.getSelectedRowCount() > 0);
        save.setEnabled(hasFile && dirty);
    }
    private void browse() {
        JFileChooser chooser = new JFileChooser();
        if (!filename.getText().trim().isEmpty()) chooser.setSelectedFile(new File(filename.getText().trim()));
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            if (!mayDiscard()) return;
            dirty = false; previewTimer.stop();
            filename.setText(chooser.getSelectedFile().getAbsolutePath()); loadCsv();
        }
    }
    /** Create a CSV safely, without replacing any existing file. First record is its header. */
    private void createCsv() {
        if (!mayDiscard()) return;
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Create New CSV File");
        chooser.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("CSV files (*.csv)", "csv"));
        chooser.setSelectedFile(new File("new-data.csv"));
        if (chooser.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File selected = chooser.getSelectedFile();
        if (!selected.getName().toLowerCase(Locale.ROOT).endsWith(".csv")) {
            selected = new File(selected.getParentFile(), selected.getName() + ".csv");
        }
        Path target = selected.toPath().toAbsolutePath().normalize();
        if (Files.exists(target)) {
            JOptionPane.showMessageDialog(this,
                "This file already exists. Choose a different name.\n" + target,
                "New CSV", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JTextField headersField = new JTextField("username,password", 36);
        JSpinner rowCount = new JSpinner(new SpinnerNumberModel(1, 0, 1000, 1));
        JPanel dialog = new JPanel(new GridBagLayout());
        GridBagConstraints dc = new GridBagConstraints();
        dc.insets = new Insets(5, 5, 5, 5);
        dc.fill = GridBagConstraints.HORIZONTAL;
        dc.gridx = 0; dc.gridy = 0; dc.weightx = 0;
        dialog.add(new JLabel("Column headers (comma-separated):"), dc);
        dc.gridx = 1; dc.weightx = 1;
        dialog.add(headersField, dc);
        dc.gridx = 0; dc.gridy = 1; dc.weightx = 0;
        dialog.add(new JLabel("Blank data rows:"), dc);
        dc.gridx = 1; dc.weightx = 1;
        dialog.add(rowCount, dc);
        if (JOptionPane.showConfirmDialog(this, dialog, "Set Up New CSV",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE) != JOptionPane.OK_OPTION) return;

        // Headers are parsed as CSV, allowing quoted values containing commas.
        List<String> headers = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(headersField.getText(),
                CSVFormat.DEFAULT.builder().setDelimiter(',').build())) {
            List<CSVRecord> records = parser.getRecords();
            if (records.size() != 1) throw new IllegalArgumentException("Enter exactly one header row.");
            for (String h : records.get(0)) {
                if (h.trim().isEmpty()) throw new IllegalArgumentException("Column names cannot be blank.");
                headers.add(h.trim());
            }
            if (headers.isEmpty() || new HashSet<>(headers).size() != headers.size())
                throw new IllegalArgumentException("Provide unique column names.");
            int blankRows = ((Number) rowCount.getValue()).intValue();
            // CREATE_NEW guarantees we never overwrite an existing CSV, even under a race.
            try (Writer writer = Files.newBufferedWriter(target, csvCharset(),
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                 CSVPrinter printer = new CSVPrinter(writer, format())) {
                printer.printRecord(headers);
                List<String> empty = new ArrayList<>(Collections.nCopies(headers.size(), ""));
                for (int i = 0; i < blankRows; i++) printer.printRecord(empty);
            }
            previewTimer.stop();
            dirty = false;
            suppress = true;
            try {
                filename.setText(target.toString());
                variableNames.setText("");
                ignoreFirstLine.setSelected(false);
            } finally { suppress = false; }
            loadCsv();
            if (model.getRowCount() > 1 && model.getColumnCount() > 0) {
                table.changeSelection(1, 0, false, false);
                table.editCellAt(1, 0);
                if (table.getEditorComponent() != null) table.getEditorComponent().requestFocusInWindow();
            }
            JOptionPane.showMessageDialog(this,
                    "CSV created successfully. The first row contains column headers.\n" +
                    "Edit the rows below and click Save changes to CSV.\n" + target,
                    "New CSV", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) { showError(ex); }
    }

    private boolean mayDiscard() {
        return !dirty || JOptionPane.showConfirmDialog(this, "Discard unsaved CSV edits?", "Unsaved CSV changes", JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION;
    }
    private char csvDelimiter() {
        String s = delimiter.getText();
        if (s.isEmpty()) return ',';
        if ("\\t".equals(s)) return '\t';
        if (s.length() != 1) throw new IllegalArgumentException("The CSV editor requires a single-character delimiter (or \\t).");
        return s.charAt(0);
    }
    private Charset csvCharset() { return encoding.getText().trim().isEmpty() ? StandardCharsets.UTF_8 : Charset.forName(encoding.getText().trim()); }
    private Path path() {
        String name = filename.getText().trim();
        if (name.isEmpty() || name.contains("${") || name.contains("__P(")) return null;
        Path p = Paths.get(name);
        if (!p.isAbsolute()) p = Paths.get(FileServer.getFileServer().getBaseDir()).resolve(p);
        return p.toAbsolutePath().normalize();
    }
    private CSVFormat format() { return CSVFormat.DEFAULT.builder().setDelimiter(csvDelimiter()).setQuote('"').setRecordSeparator("\r\n").build(); }
    private void loadCsv() {
        if (dirty) { status.setText("Save or discard unsaved edits before refreshing."); return; }
        Path p;
        try { p = path(); } catch (Exception ex) { showError(ex); return; }
        if (p == null) { clearPreview("Enter an existing literal CSV filename to preview (runtime variable paths cannot be previewed)."); return; }
        if (!Files.isRegularFile(p)) { clearPreview("File not found: " + p); return; }
        try {
            List<List<String>> data = new ArrayList<>();
            try (Reader reader = Files.newBufferedReader(p, csvCharset()); CSVParser parser = format().parse(reader)) {
                for (CSVRecord record : parser) {
                    List<String> row = new ArrayList<>(); for (String value : record) row.add(value);
                    data.add(row);
                }
            }
            int count = 0;
            for (List<String> r : data) count = Math.max(count, r.size());
            count = Math.max(count, 1);
            suppress = true;
            try {
                model.setRowCount(0); model.setColumnCount(0);
                List<String> names = new ArrayList<>();
                for (int c = 0; c < count; c++) names.add("Column " + (c + 1));
                model.setColumnIdentifiers(names.toArray());
                for (List<String> r : data) {
                    Object[] vals = new Object[count]; Arrays.fill(vals, "");
                    for (int c = 0; c < r.size(); c++) vals[c] = r.get(c);
                    model.addRow(vals);
                }
            } finally { suppress = false; }
            loadedPath = p; captureFingerprint(p); dirty = false;
            for (int i = 0; i < table.getColumnModel().getColumnCount(); i++) {
                table.getColumnModel().getColumn(i).setPreferredWidth(160);
                table.getColumnModel().getColumn(i).setMinWidth(85);
            }
            updateStatus();
        } catch (Exception ex) { clearPreview("Cannot read file: " + ex.getMessage()); showError(ex); }
    }
    private void clearPreview(String message) {
        suppress = true; try { model.setRowCount(0); model.setColumnCount(0); } finally { suppress = false; }
        loadedPath = null; dirty = false; updateButtons(); status.setText(message);
    }
    private byte[] hash(Path p) throws IOException {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            try (InputStream in = Files.newInputStream(p)) { byte[] b = new byte[8192]; int n; while ((n = in.read(b)) != -1) md.update(b, 0, n); }
            return md.digest();
        } catch (java.security.NoSuchAlgorithmException e) { throw new IOException(e); }
    }
    private void captureFingerprint(Path p) throws IOException {
        loadedSize = Files.size(p); loadedModified = Files.getLastModifiedTime(p).toMillis(); loadedHash = hash(p);
    }
    private void updateStatus() {
        updateButtons();
        status.setText((loadedPath == null ? "No CSV loaded" : loadedPath.toString()) + " | " + model.getRowCount() + " rows | " + model.getColumnCount() + " columns" + (dirty ? " | UNSAVED CHANGES" : ""));
    }
    /** Paste an Excel/Calc tab-separated rectangular range, anchored at the selected cell. */
    private void pasteClipboard() {
        if (loadedPath == null) return;
        try {
            String copied = (String) Toolkit.getDefaultToolkit().getSystemClipboard()
                    .getData(DataFlavor.stringFlavor);
            if (copied == null || copied.isEmpty()) return;
            pasteText(copied);
        } catch (Exception ex) {
            showError(new IOException("Unable to paste clipboard text: " + ex.getMessage(), ex));
        }
    }

    private void pasteText(String copied) throws IOException {
        if (table.isEditing() && !table.getCellEditor().stopCellEditing()) return;
        // Excel copies cells separated by tabs, with quotes protecting embedded line breaks/tabs.
        // If there is no tab, treat each line as a single column, not comma-separated fields.
        String content = copied;
        if (content.endsWith("\r\n")) content = content.substring(0, content.length() - 2);
        else if (content.endsWith("\n")) content = content.substring(0, content.length() - 1);
        if (content.isEmpty()) return;
        List<List<String>> rows = new ArrayList<>();
        try (CSVParser parser = CSVParser.parse(content,
                CSVFormat.DEFAULT.builder().setDelimiter('\t').setQuote('"').build())) {
            for (CSVRecord record : parser) {
                List<String> cells = new ArrayList<>();
                for (String cell : record) cells.add(cell);
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) return;
        int startRow = Math.max(0, table.getSelectedRow());
        int startCol = Math.max(0, table.getSelectedColumn());
        int columns = 0;
        for (List<String> row : rows) columns = Math.max(columns, row.size());
        if (columns == 0) return;
        if (startRow + rows.size() > 1000000 || startCol + columns > 10000)
            throw new IOException("Paste exceeds safety limits (1,000,000 rows / 10,000 columns).");

        // Model coordinates are used so the range is correct even if a table sorter is added.
        int modelRow = table.getRowCount() == 0 ? 0 : table.convertRowIndexToModel(
                Math.min(startRow, table.getRowCount() - 1));
        int modelCol = table.getColumnCount() == 0 ? 0 : table.convertColumnIndexToModel(
                Math.min(startCol, table.getColumnCount() - 1));
        int neededCols = modelCol + columns;
        if (neededCols > model.getColumnCount()) {
            int before = model.getColumnCount();
            model.setColumnCount(neededCols);
            for (int i = before; i < neededCols; i++) {
                table.getColumnModel().getColumn(i).setHeaderValue("Column " + (i + 1));
                table.getColumnModel().getColumn(i).setPreferredWidth(160);
                table.getColumnModel().getColumn(i).setMinWidth(85);
            }
        }
        while (model.getRowCount() < modelRow + rows.size()) {
            Object[] empty = new Object[model.getColumnCount()];
            Arrays.fill(empty, "");
            model.addRow(empty);
        }
        for (int r = 0; r < rows.size(); r++) {
            List<String> row = rows.get(r);
            for (int c = 0; c < row.size(); c++)
                model.setValueAt(row.get(c), modelRow + r, modelCol + c);
        }
        if (!rows.isEmpty()) {
            int lastRow = Math.min(modelRow + rows.size() - 1, table.getRowCount() - 1);
            int lastCol = Math.min(modelCol + columns - 1, table.getColumnCount() - 1);
            table.changeSelection(modelRow, modelCol, false, false);
            table.changeSelection(lastRow, lastCol, false, true);
            table.scrollRectToVisible(table.getCellRect(lastRow, lastCol, true));
        }
        updateStatus();
    }

    private void addRow() {
        if (loadedPath == null) return;
        Object[] values = new Object[model.getColumnCount()]; Arrays.fill(values, ""); model.addRow(values);
    }
    private void deleteRows() {
        if (loadedPath == null) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        int[] selected = table.getSelectedRows();
        if (selected.length == 0) return;
        if (JOptionPane.showConfirmDialog(this, "Delete " + selected.length + " row(s)? Changes reach disk when you save.", "Confirm row deletion", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        for (int i = selected.length - 1; i >= 0; i--) model.removeRow(table.convertRowIndexToModel(selected[i]));
    }
    private void saveCsv() {
        if (loadedPath == null) return;
        if (table.isEditing()) table.getCellEditor().stopCellEditing();
        Path p = loadedPath;
        Path current;
        try { current = path(); } catch (Exception ex) { showError(ex); return; }
        if (!p.equals(current)) { showError(new IOException("Filename changed since preview was loaded. Reload the new file first.")); return; }
        Path temp = null;
        try {
            if (!Files.isRegularFile(p) || Files.size(p) != loadedSize || Files.getLastModifiedTime(p).toMillis() != loadedModified || !Arrays.equals(hash(p), loadedHash)) {
                throw new IOException("CSV changed on disk since it was loaded. Save cancelled to protect external changes. Back up your edits before refreshing.");
            }
            Path parent = p.getParent();
            temp = Files.createTempFile(parent, ".csv-editor-", ".tmp");
            try (Writer writer = Files.newBufferedWriter(temp, csvCharset()); CSVPrinter printer = new CSVPrinter(writer, format())) {
                for (int row = 0; row < model.getRowCount(); row++) {
                    List<String> values = new ArrayList<>();
                    for (int col = 0; col < model.getColumnCount(); col++) values.add(Objects.toString(model.getValueAt(row, col), ""));
                    printer.printRecord(values);
                }
            }
            try { Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, p, StandardCopyOption.REPLACE_EXISTING); }
            temp = null; captureFingerprint(p); dirty = false; updateStatus();
            JOptionPane.showMessageDialog(this, "CSV saved successfully to:\n" + p);
        } catch (Exception ex) { showError(ex); }
        finally { if (temp != null) try { Files.deleteIfExists(temp); } catch (IOException ignored) { } }
    }
    private void showError(Exception e) { JOptionPane.showMessageDialog(this, e.getMessage(), "CSV Editor", JOptionPane.ERROR_MESSAGE); }

    @Override public String getStaticLabel() { return "CSV Data Set Config (Editable)"; }
    @Override public String getLabelResource() { return "displayName"; }
    @Override public Collection<String> getMenuCategories() { return Collections.singletonList(MenuFactory.CONFIG_ELEMENTS); }
    @Override public TestElement createTestElement() {
        CSVDataSet element = new CSVDataSet(); modifyTestElement(element); return element;
    }
    @Override public void modifyTestElement(TestElement element) {
        configureTestElement(element);
        element.setProperty(new StringProperty(TestElement.GUI_CLASS, getClass().getName()));
        element.setProperty(new StringProperty(TestElement.TEST_CLASS, CSVDataSet.class.getName()));
        element.setProperty(new StringProperty("filename", filename.getText()));
        element.setProperty(new StringProperty("fileEncoding", encoding.getText()));
        element.setProperty(new StringProperty("variableNames", variableNames.getText()));
        element.setProperty(new StringProperty("delimiter", delimiter.getText()));
        element.setProperty(new BooleanProperty("ignoreFirstLine", ignoreFirstLine.isSelected()));
        element.setProperty(new BooleanProperty("quotedData", quotedData.isSelected()));
        element.setProperty(new BooleanProperty("recycle", recycle.isSelected()));
        element.setProperty(new BooleanProperty("stopThread", stopThread.isSelected()));
        String[] modes = {"shareMode.all", "shareMode.group", "shareMode.thread", identifier.getText().trim()};
        element.setProperty(new StringProperty("shareMode", modes[sharing.getSelectedIndex()]));
    }
    @Override public void configure(TestElement element) {
        super.configure(element); suppress = true;
        try {
            filename.setText(element.getPropertyAsString("filename"));
            encoding.setText(element.getPropertyAsString("fileEncoding"));
            variableNames.setText(element.getPropertyAsString("variableNames"));
            delimiter.setText(element.getPropertyAsString("delimiter", ","));
            ignoreFirstLine.setSelected(element.getPropertyAsBoolean("ignoreFirstLine"));
            quotedData.setSelected(element.getPropertyAsBoolean("quotedData"));
            recycle.setSelected(element.getPropertyAsBoolean("recycle", true));
            stopThread.setSelected(element.getPropertyAsBoolean("stopThread"));
            String mode = element.getPropertyAsString("shareMode", "shareMode.all");
            if ("shareMode.all".equals(mode)) sharing.setSelectedIndex(0);
            else if ("shareMode.group".equals(mode)) sharing.setSelectedIndex(1);
            else if ("shareMode.thread".equals(mode)) sharing.setSelectedIndex(2);
            else { sharing.setSelectedIndex(3); identifier.setText(mode); }
        } finally { suppress = false; }
        identifier.setEnabled(sharing.getSelectedIndex() == 3);
        previewTimer.stop(); dirty = false; loadCsv();
    }
    @Override public void clearGui() {
        super.clearGui(); suppress = true;
        try {
            filename.setText(""); encoding.setText(""); variableNames.setText(""); delimiter.setText(",");
            ignoreFirstLine.setSelected(false); quotedData.setSelected(false); recycle.setSelected(true); stopThread.setSelected(false);
            sharing.setSelectedIndex(0); identifier.setText("");
        } finally { suppress = false; }
        clearPreview("Choose a CSV file to preview its contents.");
    }
}

package com.example.jmeter;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.jmeter.save.CSVSaveService;
import org.apache.jorphan.util.JOrphanUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * CSV editing codec matching CSVDataSet's two runtime read modes.
 *
 * Quoted mode uses the SAME JMeter CSVSaveService reader that FileServer uses for
 * quoted CSVDataSet records. Unquoted mode matches CSVDataSet's physical-line
 * reader followed by JOrphanUtils.split(..., false). A plain, unquoted CSV file
 * cannot represent a delimiter or a line break inside one field, so this codec
 * refuses to save such values rather than changing their meaning at runtime.
 */
final class CsvEditorCodec {
    private CsvEditorCodec() { }

    static List<List<String>> read(Path path, Charset encoding, char delimiter,
                                   boolean quoted) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(path, encoding)) {
            if (quoted) {
                while (true) {
                    String[] values = CSVSaveService.csvReadFile(reader, delimiter);
                    if (values.length == 0) break; // EOF, not an empty CSV field
                    rows.add(new ArrayList<>(Arrays.asList(values)));
                }
            } else {
                String line;
                while ((line = reader.readLine()) != null) {
                    String[] values = JOrphanUtils.split(line,
                            String.valueOf(delimiter), false);
                    rows.add(new ArrayList<>(Arrays.asList(values)));
                }
            }
        }
        return rows;
    }

    static void validateForMode(List<List<String>> rows, char delimiter,
                                boolean quoted) throws IOException {
        if (quoted) return;
        for (int r = 0; r < rows.size(); r++) {
            for (int c = 0; c < rows.get(r).size(); c++) {
                String value = rows.get(r).get(c);
                if (value == null) continue;
                if (value.indexOf(delimiter) >= 0 || value.indexOf('\r') >= 0
                        || value.indexOf('\n') >= 0) {
                    throw new IOException("Row " + (r + 1) + ", column " + (c + 1)
                            + " contains a delimiter or line break. JMeter's unquoted "
                            + "CSV reader would split this into different values. "
                            + "Enable 'Allow quoted data?' and reload the file before "
                            + "entering this value, or remove the delimiter/line break.");
                }
            }
        }
    }

    static void write(Path path, Charset encoding, char delimiter,
                      boolean quoted, List<List<String>> rows) throws IOException {
        validateForMode(rows, delimiter, quoted);
        try (Writer writer = Files.newBufferedWriter(path, encoding,
                StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
            write(writer, delimiter, quoted, rows);
        }
    }

    static void write(Writer writer, char delimiter, boolean quoted,
                      List<List<String>> rows) throws IOException {
        validateForMode(rows, delimiter, quoted);
        if (quoted) {
            CSVFormat format = CSVFormat.DEFAULT.builder().setDelimiter(delimiter)
                    .setQuote('"').setRecordSeparator("\r\n").build();
            // Closing the printer flushes the writer but does not affect the
            // caller's responsibility for safely opening/replacing its file.
            CSVPrinter printer = new CSVPrinter(writer, format);
            for (List<String> row : rows) printer.printRecord(row);
            printer.flush();
        } else {
            for (List<String> row : rows) {
                for (int i = 0; i < row.size(); i++) {
                    if (i != 0) writer.write(delimiter);
                    writer.write(row.get(i) == null ? "" : row.get(i));
                }
                writer.write("\r\n");
            }
            writer.flush();
        }
    }
}

package com.example.jmeter;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.*;

/** Executes against real JMeter CSVSaveService and JOrphanUtils (Maven test classpath). */
public class CsvEditorCodecTest {
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private Path fixture(String text) throws IOException {
        Path p = folder.newFile("data-" + System.nanoTime() + ".csv").toPath();
        Files.write(p, text.getBytes(StandardCharsets.UTF_8));
        return p;
    }

    private List<List<String>> read(Path p, boolean quoted) throws IOException {
        return CsvEditorCodec.read(p, StandardCharsets.UTF_8, ',', quoted);
    }

    @Test public void quotedCommaEscapedQuoteAndTrailingBlankMatchJmeter() throws Exception {
        Path p = fixture("name,notes,ending\r\n\"John, Smith\",\"He said \"\"Hi\"\"\",\r\n");
        List<List<String>> rows = read(p, true);
        assertEquals(Arrays.asList("name", "notes", "ending"), rows.get(0));
        assertEquals(Arrays.asList("John, Smith", "He said \"Hi\"", ""), rows.get(1));
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', true, rows);
        assertEquals(rows, read(p, true));
    }

    @Test public void quotedMultilineValueSurvivesRoundTrip() throws Exception {
        Path p = fixture("id,description\n1,\"first\nsecond\"\n");
        List<List<String>> before = read(p, true);
        assertEquals(2, before.size());
        assertEquals("first\nsecond", before.get(1).get(1));
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', true, before);
        assertEquals(before, read(p, true));
    }

    @Test public void unquotedModeDoesNotInterpretDoubleQuotes() throws Exception {
        Path p = fixture("a,\"b,c\",d\r\n");
        List<List<String>> rows = read(p, false);
        assertEquals(Arrays.asList("a", "\"b", "c\"", "d"), rows.get(0));
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', false, rows);
        assertEquals("a,\"b,c\",d\r\n", new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
        assertEquals(rows, read(p, false));
    }

    @Test public void unquotedLiteralQuoteIsNeverEscaped() throws Exception {
        Path p = fixture("say \"Hi\",ok\n");
        List<List<String>> rows = read(p, false);
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', false, rows);
        assertEquals("say \"Hi\",ok\r\n", new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
    }

    @Test public void unquotedDelimiterInsideCellRejectedBeforeOpeningFile() throws Exception {
        Path p = fixture("old,data\n");
        try {
            CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', false,
                    Collections.singletonList(Arrays.asList("Jane, Doe", "value")));
            fail("Expected checked validation error");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Allow quoted data"));
        }
        assertEquals("old,data\n", new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
    }

    @Test public void unquotedNewlineInsideCellRejectedBeforeOpeningFile() throws Exception {
        Path p = fixture("old,data\n");
        try {
            CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', false,
                    Collections.singletonList(Arrays.asList("first\r\nsecond", "value")));
            fail("Expected checked validation error");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Row 1, column 1"));
        }
        assertEquals("old,data\n", new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
    }

    @Test public void quotedUnicodeAndEmptyFinalColumnSurvive() throws Exception {
        Path p = fixture("one,two,three\nअमित,\"你好 😀\",\n");
        List<List<String>> before = read(p, true);
        assertEquals(Arrays.asList("अमित", "你好 😀", ""), before.get(1));
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ',', true, before);
        assertEquals(before, read(p, true));
    }

    @Test public void alternativeSemicolonDelimiterAndQuotedValues() throws Exception {
        Path p = fixture("a;b;c\n1;\"2;3\";\"x\"\n");
        List<List<String>> before = CsvEditorCodec.read(p, StandardCharsets.UTF_8, ';', true);
        assertEquals(Arrays.asList("1", "2;3", "x"), before.get(1));
        CsvEditorCodec.write(p, StandardCharsets.UTF_8, ';', true, before);
        assertEquals(before, CsvEditorCodec.read(p, StandardCharsets.UTF_8, ';', true));
    }

    @Test public void newCsvUnquotedHeadersWithDelimiterMustBeRejected() throws Exception {
        try {
            CsvEditorCodec.validateForMode(Collections.singletonList(
                    Arrays.asList("one", "two,three")), ',', false);
            fail("Expected header validation");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("Row 1, column 2"));
        }
    }

    @Test public void quotedEmptyFieldStaysAFieldNotEof() throws Exception {
        Path p = fixture("\"\"\n");
        List<List<String>> rows = read(p, true);
        assertEquals(1, rows.size());
        assertEquals(Collections.singletonList(""), rows.get(0));
    }
}

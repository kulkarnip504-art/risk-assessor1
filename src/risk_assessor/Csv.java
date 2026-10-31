package risk_assessor;

import java.nio.file.*;
import java.util.*;

/** Tiny CSV reader that understands "quoted, values" and Windows line endings. */
public class Csv {
    public static List<Map<String, String>> read(String path) throws Exception {
        String text = Files.readString(Path.of(path)).replace("\r\n", "\n").replace("\r", "\n");
        List<List<String>> rows = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder cell = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { cell.append('"'); i++; }
                else if (c == '"') quoted = false;
                else cell.append(c);
            } else if (c == '"') quoted = true;
            else if (c == ',') { row.add(cell.toString()); cell.setLength(0); }
            else if (c == '\n') { row.add(cell.toString()); cell.setLength(0); rows.add(row); row = new ArrayList<>(); }
            else cell.append(c);
        }
        if (cell.length() > 0 || !row.isEmpty()) { row.add(cell.toString()); rows.add(row); }
        List<String> head = rows.get(0);
        List<Map<String, String>> out = new ArrayList<>();
        for (int r = 1; r < rows.size(); r++) {
            if (rows.get(r).size() != head.size()) continue;   // skip broken lines
            Map<String, String> m = new LinkedHashMap<>();
            for (int c = 0; c < head.size(); c++) m.put(head.get(c).trim(), rows.get(r).get(c).trim());
            out.add(m);
        }
        return out;
    }
}

package swp.lexer.automata;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

import swp.util.*;

/**
 * Written for a practical assignment. Allows the processing of lexer grammar files.
 *
 * Each line (representing a token) of the input file has the following format:
 * <pre>
 *     [name of the enum item]\t[description]\t[regular expression matching the tokens content]
 * </pre>
 * The first token has to be the EOF token by default.
 * If the regexp is missing, the token is only added to the enum (it may only appear as the last terminal).
 */
public class EnumTableGenerator {

    public static void main(String[] args){
        try {
            processFile(Paths.get(args[0]), args[1],
                    args[2], Paths.get(args[3]), Paths.get(args[4]),
                    args[5], Paths.get(args[6]), Paths.get(args[7]));
        } catch (IOException | IllegalArgumentException | ArrayIndexOutOfBoundsException e) {
            e.printStackTrace();

            System.err.println("Usage: ./prog [input file] [package name]" +
                    " [table class name] [table template file] [table output] " +
                    "[enum class name] [enum template] [enum output file]");
        }
    }

    public static void processFile(Path inputFile, String packageName,
                                   String tableClassName, Path tableTemplate, Path tableOutput,
                                   String enumClassName, Path enumTemplate, Path enumOutput)
            throws IOException, IllegalArgumentException {
        List<TerminalDescription> descrs = new ArrayList<>();
        for (String line : Files.readAllLines(inputFile)) {
            String[] parts = line.split("#");
            if (parts.length != 2 && parts.length != 3){
                throw new IllegalArgumentException(String.format("Wrong line format: %s", line));
            }
            if (parts.length == 3){
                descrs.add(new TerminalDescription(parts[0], parts[1], parts[2], false));
            } else {
                descrs.add(new TerminalDescription(parts[0], parts[1], "", true));
            }
        }
        createTableFile(descrs, packageName, enumClassName, tableClassName, tableTemplate, tableOutput);
        createEnumFile(descrs, packageName, enumClassName, enumTemplate, enumOutput);
    }

    private static void createTableFile(List<TerminalDescription> descriptions, String packageName, String enumClassName,
                                        String tableClassName, Path tableTemplate, Path tableOutput) throws IOException {
        LexerDescriptionParser parser = new LexerDescriptionParser();
        List<Pair<String, String>> pairs = new ArrayList<>();
        for (TerminalDescription description : descriptions) {
            if (!description.onlyInEnum){
                pairs.add(new Pair<>(description.name, description.regexp));
            }
        }
        Table table = parser.eval(pairs, false);
        Automaton automaton = parser.automaton.toDeterministicVersion();
        automaton.toImage("test", "svg");
        table = automaton.toTable();
        Files.write(tableOutput, table.toTableClass(descriptions, enumClassName,
                tableTemplate, packageName, tableClassName).getBytes());
    }

    private static void createEnumFile(List<TerminalDescription> descriptions, String packageName,
                                       String enumClassName, Path enumTemplate, Path enumOutputFile) throws IOException {
        String enumInits = descriptions.stream().map(descr -> {
            return String.format("    %s(%s)", descr.name, Utils.toPrintableRepresentation(descr.description));
        }).collect(Collectors.joining(",\n"));
        String template = new String(Files.readAllBytes(enumTemplate));
        template = template.replaceAll("package[^;]+;", String.format("package %s;", packageName))
                .replaceAll("EnumTemplate", enumClassName)
                .replace("    EOF(\"eof\")", enumInits);
        Files.write(enumOutputFile, template.replaceAll("\t", "    ").getBytes());
    }

    public static class TerminalDescription {
        public final String name;
        public final String description;
        public final String regexp;
        public final boolean onlyInEnum;

        private TerminalDescription(String name, String description, String regexp, boolean onlyInEnum) {
            this.name = name;
            this.description = description;
            this.regexp = regexp;
            this.onlyInEnum = onlyInEnum;
        }

        @Override
        public String toString() {
            return "TerminalDescription{" +
                    "name='" + name + '\'' +
                    ", description='" + description + '\'' +
                    ", regexp='" + regexp + '\'' +
                    ", onlyInEnum=" + onlyInEnum +
                    '}';
        }
    }
}

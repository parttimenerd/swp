package nildumu;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import swp.util.Pair;

import static nildumu.Lattices.*;
import static nildumu.LeakageCalculation.rule;
import static nildumu.ui.ViewImage.view;

public class PythonCaller {

    public static int calcLeakageShowImage(Context context, Sec<?> sec) throws IOException, InterruptedException {
        Pair<Integer, Path> p = calcLeakageWithImage(context, sec);
        view(p.second);
        return p.first;
    }

    public static Pair<Integer, Path> calcLeakageWithImage(Context context, Sec<?> level) throws IOException, InterruptedException {
        Path in = createJSONFile(context, level);
        Path img = Files.createTempFile("", ".png");
        List<String> lines = exec("python3","frontend.py", in.toString(), "min_node_cut_vis", "--out", img.toString());
        int leakage = Integer.parseInt(lines.get(lines.size() - 1).split(":")[1].trim());
        return new Pair<>(leakage, img);
    }

    private static List<String> exec(String... args) throws IOException, InterruptedException {
        String basepath = "/home/parttimenerd/Documents/Studium/SS18/MA/mpsat_solvers";
        ProcessBuilder builder = new ProcessBuilder(args);
        builder.environment().put("LC_ALL", "C.UTF-8");
        builder.environment().put("LANG", "C.UTF-8");
        builder.directory(Paths.get(basepath).toFile());
        Process p = builder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        p.waitFor();
        System.out.println(builder.command() + " # " + new BufferedReader(new InputStreamReader(p.getErrorStream())).lines().collect(Collectors.joining("\n")));
        return reader.lines().collect(Collectors.toList());
    }

    private static Path createJSONFile(Context context, Sec<?> level){
        try {
            return createJSONFile(Files.createTempFile(null, ".json"), context, level);
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    public static Path createJSONFile(Path path, Context context, Sec<?> level){
        try {
            StringBuilder builder = new StringBuilder();
            builder.append("{").append("\"bits\": {");
            List<String> bitRules = new ArrayList<>();
            context.walkBits(bit -> {
                bitRules.add(String.format("\"%s\": [%s]", bit, toJson(rule(bit))));
            }, bit -> !bit.isUnknown());
            builder.append(String.join(",\n", bitRules));
            builder.append(String.format("},\n\"high\":[%s], \n\"output\": [%s]\n}",
                    toJson(context.getInputBits(level)), toJson(context.getOutputBits(level))));
            Files.write(path, Collections.singleton(builder));
            return path;
        } catch (IOException e) {
            e.printStackTrace();
            throw new RuntimeException();
        }
    }

    private static String toJson(Collection<?> col){
        return col.stream().map(r -> "\"" + r + "\"" ).collect(Collectors.joining(", "));
    }


}
